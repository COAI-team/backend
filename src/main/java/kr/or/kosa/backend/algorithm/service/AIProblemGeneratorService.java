package kr.or.kosa.backend.algorithm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.algorithm.dto.AlgoProblemDto;
import kr.or.kosa.backend.algorithm.dto.AlgoTestcaseDto;
import kr.or.kosa.backend.algorithm.dto.LLMResponseDto;
import kr.or.kosa.backend.algorithm.dto.request.ProblemGenerationRequestDto;
import kr.or.kosa.backend.algorithm.dto.response.ProblemGenerationResponseDto;
import kr.or.kosa.backend.algorithm.dto.ProblemValidationLogDto;
import kr.or.kosa.backend.algorithm.dto.enums.ProblemDifficulty;
import kr.or.kosa.backend.algorithm.mapper.AlgorithmProblemMapper;
import kr.or.kosa.backend.algorithm.mapper.ProblemValidationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIProblemGeneratorService {

    // === Phase 2 통합 서비스들 ===
    private final LLMChatService llmChatService;
    private final ProblemGenerationPromptBuilder promptBuilder;
    private final AlgorithmSynonymDictionary synonymDictionary;
    private final ProblemVectorStoreService vectorStoreService;
    private final LLMResponseParser responseParser;

    // === 기존 의존성 ===
    private final ObjectMapper objectMapper;
    private final AlgorithmProblemMapper algorithmProblemMapper;
    private final ProblemValidationLogMapper validationLogMapper;

    @Value("${algorithm.generation.rag-enabled:true}")
    private boolean ragEnabled;

    @Value("${algorithm.generation.few-shot-count:3}")
    private int fewShotCount;

    /**
     * AI 문제 생성 (Phase 2 통합 버전)
     * - LLMChatService 사용
     * - ProblemGenerationPromptBuilder로 구조화된 프롬프트 생성
     * - AlgorithmSynonymDictionary로 주제 확장
     * - RAG 기반 Few-shot 학습 지원
     */
    public ProblemGenerationResponseDto generateProblem(ProblemGenerationRequestDto request) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("AI 문제 생성 시작 - 난이도: {}, 주제: {}, RAG: {}",
                    request.getDifficulty(), request.getTopic(), ragEnabled);

            // 1. 주제 확장 (동의어 사전 활용)
            Set<String> expandedTopics = synonymDictionary.expand(request.getTopic());
            log.debug("주제 확장: {} → {}", request.getTopic(), expandedTopics);

            // 2. RAG 기반 Few-shot 예시 검색 (활성화된 경우)
            List<Document> fewShotExamples = null;
            if (ragEnabled && !"SQL".equalsIgnoreCase(request.getProblemType())) {
                try {
                    String searchQuery = synonymDictionary.buildSearchQuery(
                            request.getTopic(),
                            request.getDifficulty().name()
                    );
                    fewShotExamples = vectorStoreService.getFewShotExamples(
                            searchQuery,
                            request.getDifficulty().name(),
                            fewShotCount
                    );
                    log.info("RAG 검색 완료 - {}개 예시 문제 획득", fewShotExamples.size());
                } catch (Exception e) {
                    log.warn("RAG 검색 실패, 지침 기반으로 진행: {}", e.getMessage());
                    fewShotExamples = null;
                }
            }

            // 3. 프롬프트 생성 (Phase 2 통합)
            String systemPrompt = promptBuilder.buildSystemPrompt();
            String userPrompt = (fewShotExamples != null && !fewShotExamples.isEmpty())
                    ? promptBuilder.buildUserPrompt(request, fewShotExamples)
                    : buildLegacyPrompt(request);

            // 4. LLM 호출 (LLMChatService 사용)
            LLMResponseDto llmResponse = llmChatService.generateWithMetadata(systemPrompt, userPrompt);
            String aiResponse = llmResponse.getContent();

            log.info("LLM 응답 완료 - 토큰: {}, 응답시간: {}ms",
                    llmResponse.getTotalTokens(), llmResponse.getResponseTimeMs());

            // 5. 응답 파싱 및 DTO 생성 (LLMResponseParser 사용)
            LLMResponseParser.ParsedResult parsedData = responseParser.parse(aiResponse, request);
            AlgoProblemDto problem = parsedData.problem();
            List<AlgoTestcaseDto> testCases = parsedData.testCases();

            double generationTime = (System.currentTimeMillis() - startTime) / 1000.0;

            log.info("AI 문제 생성 완료 - 제목: {}, 테스트케이스: {}개, 소요시간: {}초",
                    problem.getAlgoProblemTitle(), testCases.size(), generationTime);

            return ProblemGenerationResponseDto.builder()
                    .problem(problem)
                    .testCases(testCases)
                    .optimalCode(parsedData.optimalCode())
                    .naiveCode(parsedData.naiveCode())
                    .generationTime(generationTime)
                    .generatedAt(LocalDateTime.now())
                    .status(ProblemGenerationResponseDto.GenerationStatus.SUCCESS)
                    .build();

        } catch (Exception e) {
            log.error("AI 문제 생성 실패", e);

            double generationTime = (System.currentTimeMillis() - startTime) / 1000.0;

            return ProblemGenerationResponseDto.builder()
                    .generationTime(generationTime)
                    .generatedAt(LocalDateTime.now())
                    .status(ProblemGenerationResponseDto.GenerationStatus.FAILED)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * 레거시 프롬프트 생성 (RAG 없이 지침만 사용)
     */
    private String buildLegacyPrompt(ProblemGenerationRequestDto request) {
        // DATABASE 문제인 경우
        if ("SQL".equalsIgnoreCase(request.getProblemType())) {
            return buildDatabasePrompt(getDifficultyDescription(request.getDifficulty()), request, "");
        }

        // ALGORITHM 문제인 경우
        return promptBuilder.buildUserPromptWithoutRag(request);
    }

    /**
     * AI 문제 생성 (스트리밍 버전 - Phase 2 통합)
     * SSE를 통해 실시간으로 생성 과정을 클라이언트에 전송
     */
    public Flux<String> generateProblemStream(ProblemGenerationRequestDto request) {
        return Flux.create(sink -> {
            // 별도 스레드에서 실행
            Schedulers.boundedElastic().schedule(() -> {
                long startTime = System.currentTimeMillis();

                try {
                    // 1단계: 주제 확장
                    emitStep(sink, "주제 분석 중...");
                    Set<String> expandedTopics = synonymDictionary.expand(request.getTopic());
                    log.debug("주제 확장: {} → {}", request.getTopic(), expandedTopics);

                    // 2단계: RAG 검색 (알고리즘 문제인 경우)
                    List<Document> fewShotExamples = null;
                    if (ragEnabled && !"SQL".equalsIgnoreCase(request.getProblemType())) {
                        emitStep(sink, "유사 문제 검색 중...");
                        try {
                            String searchQuery = synonymDictionary.buildSearchQuery(
                                    request.getTopic(),
                                    request.getDifficulty().name()
                            );
                            fewShotExamples = vectorStoreService.getFewShotExamples(
                                    searchQuery,
                                    request.getDifficulty().name(),
                                    fewShotCount
                            );
                            log.info("RAG 검색 완료 - {}개 예시 문제 획득", fewShotExamples.size());
                        } catch (Exception e) {
                            log.warn("RAG 검색 실패, 지침 기반으로 진행: {}", e.getMessage());
                        }
                    }

                    // 3단계: 프롬프트 생성
                    emitStep(sink, "프롬프트 생성 중...");
                    String systemPrompt = promptBuilder.buildSystemPrompt();
                    String userPrompt = (fewShotExamples != null && !fewShotExamples.isEmpty())
                            ? promptBuilder.buildUserPrompt(request, fewShotExamples)
                            : buildLegacyPrompt(request);

                    // 4단계: LLM 호출
                    emitStep(sink, "AI에게 문제 생성 요청 중...");
                    LLMResponseDto llmResponse = llmChatService.generateWithMetadata(systemPrompt, userPrompt);
                    String aiResponse = llmResponse.getContent();

                    log.info("LLM 응답 완료 - 토큰: {}, 응답시간: {}ms",
                            llmResponse.getTotalTokens(), llmResponse.getResponseTimeMs());

                    // 5단계: 파싱 (LLMResponseParser 사용)
                    emitStep(sink, "문제 정보 파싱 중...");
                    LLMResponseParser.ParsedResult parsedData = responseParser.parse(aiResponse, request);
                    AlgoProblemDto problem = parsedData.problem();
                    List<AlgoTestcaseDto> testCases = parsedData.testCases();

                    // 6단계: DB 저장
                    emitStep(sink, "데이터베이스에 저장 중...");

                    // 문제 저장 (MyBatis useGeneratedKeys로 ID 자동 설정)
                    algorithmProblemMapper.insertProblem(problem);
                    Long problemId = problem.getAlgoProblemId();

                    // 테스트케이스 저장
                    for (AlgoTestcaseDto tc : testCases) {
                        tc.setAlgoProblemId(problemId);
                        algorithmProblemMapper.insertTestcase(tc);
                    }

                    // 7단계: 검증 로그 저장 (검증 코드가 있는 경우)
                    log.info("검증 로그 저장 조건 확인 - optimalCode: {}, naiveCode: {}",
                            parsedData.optimalCode() != null ? "있음(" + parsedData.optimalCode().length() + "자)" : "null",
                            parsedData.naiveCode() != null ? "있음(" + parsedData.naiveCode().length() + "자)" : "null");

                    if (parsedData.optimalCode() != null || parsedData.naiveCode() != null) {
                        emitStep(sink, "검증 로그 저장 중...");
                        saveValidationLog(problemId, parsedData);
                    } else {
                        log.warn("검증 코드가 없어 검증 로그를 저장하지 않습니다. 문제 ID: {}", problemId);
                    }

                    double generationTime = (System.currentTimeMillis() - startTime) / 1000.0;

                    // 8단계: 완료 이벤트 전송
                    Map<String, Object> completeData = new HashMap<>();
                    completeData.put("problemId", problemId);
                    completeData.put("title", problem.getAlgoProblemTitle());
                    completeData.put("description", problem.getAlgoProblemDescription());
                    completeData.put("difficulty", problem.getAlgoProblemDifficulty());
                    completeData.put("testCaseCount", testCases.size());
                    completeData.put("generationTime", generationTime);
                    completeData.put("hasValidationCode", parsedData.optimalCode() != null);

                    emitComplete(sink, completeData);

                    log.info("스트리밍 문제 생성 완료 - 문제 ID: {}, 소요시간: {}초", problemId, generationTime);

                } catch (Exception e) {
                    log.error("스트리밍 문제 생성 실패", e);
                    emitError(sink, e.getMessage());
                }
            });
        });
    }

    /**
     * 검증 로그 저장
     */
    private void saveValidationLog(Long problemId, LLMResponseParser.ParsedResult data) {
        try {
            ProblemValidationLogDto validationLog = ProblemValidationLogDto.builder()
                    .algoProblemId(problemId)
                    .optimalCode(data.optimalCode())
                    .naiveCode(data.naiveCode())
                    .expectedTimeComplexity(data.expectedTimeComplexity())
                    .validationStatus("PENDING")
                    .correctionAttempts(0)
                    .createdAt(LocalDateTime.now())
                    .build();

            validationLogMapper.insertValidationLog(validationLog);
            log.info("검증 로그 저장 완료 - 문제 ID: {}, 검증 ID: {}",
                    problemId, validationLog.getValidationId());
        } catch (Exception e) {
            log.error("검증 로그 저장 실패 - 문제 ID: {}", problemId, e);
            // 검증 로그 저장 실패는 전체 프로세스를 중단하지 않음
        }
    }

    /**
     * SSE 단계 이벤트 전송
     */
    private void emitStep(reactor.core.publisher.FluxSink<String> sink, String message) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "STEP");
            event.put("message", message);
            sink.next(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            log.error("이벤트 JSON 변환 실패", e);
        }
    }

    /**
     * SSE 완료 이벤트 전송
     */
    private void emitComplete(reactor.core.publisher.FluxSink<String> sink, Map<String, Object> data) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "COMPLETE");
            event.put("data", data);
            sink.next(objectMapper.writeValueAsString(event));
            sink.complete();
        } catch (JsonProcessingException e) {
            log.error("완료 이벤트 JSON 변환 실패", e);
            sink.complete();
        }
    }

    /**
     * SSE 에러 이벤트 전송
     */
    private void emitError(reactor.core.publisher.FluxSink<String> sink, String message) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "ERROR");
            event.put("message", message);
            sink.next(objectMapper.writeValueAsString(event));
            sink.complete();
        } catch (JsonProcessingException e) {
            log.error("에러 이벤트 JSON 변환 실패", e);
            sink.complete();
        }
    }

    /**
     * DATABASE 문제 프롬프트 생성
     */
    private String buildDatabasePrompt(String difficultyDesc, ProblemGenerationRequestDto request,
            String existingTitlesStr) {
        return String.format(
                """
                        당신은 SQL/DATABASE 문제 출제 전문가입니다.
                        다음 조건에 맞는 DATABASE 문제를 **반드시 JSON 형식으로만** 생성해주세요.

                        ## 요구사항
                        - 난이도: %s
                        - 주제: %s
                        %s
                        %s

                        ## 중요 규칙
                        1. 문제는 실제 SQL 코딩 테스트 수준으로 작성
                        2. 테이블 구조는 명확하고 실용적으로 설계
                        3. 초기화 스크립트(DDL/DML)를 반드시 포함
                        4. 테스트케이스는 최소 3개 이상 포함
                        5. **JSON 형식 외 다른 텍스트 절대 포함 금지**

                        ## 응답 형식 (반드시 이 JSON 구조로만 응답)
                        {
                          "title": "문제 제목",
                          "description": "문제 설명 (자세하게)",
                          "constraints": "제약 조건",
                          "inputFormat": "SQL 쿼리 작성 방법",
                          "outputFormat": "결과 형식 설명",
                          "initScript": "CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(50));\\nINSERT INTO users VALUES (1, 'Alice'), (2, 'Bob');",
                          "sampleInput": "SELECT * FROM users WHERE id = 1;",
                          "sampleOutput": "1, Alice",
                          "testCases": [
                            {"input": "SELECT * FROM users WHERE id = 2;", "output": "2, Bob"},
                            {"input": "SELECT COUNT(*) FROM users;", "output": "2"},
                            {"input": "SELECT name FROM users ORDER BY id DESC LIMIT 1;", "output": "Bob"}
                          ]
                        }

                        **주의**: JSON만 출력하고 다른 설명은 절대 포함하지 마세요!
                        **initScript는 반드시 포함해야 하며, CREATE TABLE과 INSERT 문을 모두 포함해야 합니다!**
                        """,
                difficultyDesc,
                request.getTopic(),
                request.getAdditionalRequirements() != null
                        ? "- 추가 요구사항: " + request.getAdditionalRequirements()
                        : "",
                existingTitlesStr);
    }

    /**
     * 난이도 설명
     */
    private String getDifficultyDescription(ProblemDifficulty difficulty) {
        return switch (difficulty) {
            case BRONZE -> "초급 (기본 문법, 간단한 구현)";
            case SILVER -> "초중급 (기본 알고리즘, 자료구조)";
            case GOLD -> "중급 (고급 알고리즘, 최적화)";
            case PLATINUM -> "고급 (복잡한 알고리즘, 수학적 사고)";
        };
    }
}