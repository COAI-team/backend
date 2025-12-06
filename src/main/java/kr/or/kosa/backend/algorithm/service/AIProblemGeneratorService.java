package kr.or.kosa.backend.algorithm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.algorithm.dto.AlgoProblemDto;
import kr.or.kosa.backend.algorithm.dto.AlgoTestcaseDto;
import kr.or.kosa.backend.algorithm.dto.enums.ProblemDifficulty;
import kr.or.kosa.backend.algorithm.dto.enums.ProblemSource;
import kr.or.kosa.backend.algorithm.dto.enums.ProblemType;
import kr.or.kosa.backend.algorithm.dto.ProblemGenerationRequestDto;
import kr.or.kosa.backend.algorithm.dto.ProblemGenerationResponseDto;
import kr.or.kosa.backend.algorithm.mapper.AlgorithmProblemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIProblemGeneratorService {

    private final OpenAiChatModel chatModel; // ✅ Spring AI 자동 주입
    private final ObjectMapper objectMapper;
    private final AlgorithmProblemMapper algorithmProblemMapper;

    /**
     * AI 문제 생성
     */
    public ProblemGenerationResponseDto generateProblem(ProblemGenerationRequestDto request) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("AI 문제 생성 시작 - 난이도: {}, 주제: {}", request.getDifficulty(), request.getTopic());

            // 1. 프롬프트 생성
            String prompt = buildPrompt(request);

            // 2. ✅ 실제 OpenAI API 호출
            String aiResponse = callOpenAI(prompt);

            // 3. 응답 파싱
            AlgoProblemDto problem = parseAIProblemResponse(aiResponse, request);
            List<AlgoTestcaseDto> testCases = parseAITestCaseResponse(aiResponse);

            double generationTime = (System.currentTimeMillis() - startTime) / 1000.0;

            log.info("AI 문제 생성 완료 - 제목: {}, 소요시간: {}초",
                    problem.getAlgoProblemTitle(), generationTime);

            return ProblemGenerationResponseDto.builder()
                    .problem(problem)
                    .testCases(testCases)
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
     * AI 문제 생성 (스트리밍 버전)
     * SSE를 통해 실시간으로 생성 과정을 클라이언트에 전송
     */
    public Flux<String> generateProblemStream(ProblemGenerationRequestDto request) {
        return Flux.create(sink -> {
            // 별도 스레드에서 실행
            Schedulers.boundedElastic().schedule(() -> {
                long startTime = System.currentTimeMillis();

                try {
                    // 1단계: 시작 알림
                    emitStep(sink, "프롬프트 생성 중...");
                    Thread.sleep(300); // 시각적 피드백을 위한 짧은 대기

                    // 2단계: 프롬프트 생성
                    String prompt = buildPrompt(request);
                    emitStep(sink, "AI에게 문제 생성 요청 중...");

                    // 3단계: OpenAI API 호출
                    String aiResponse = callOpenAI(prompt);
                    emitStep(sink, "응답 분석 중...");
                    Thread.sleep(200);

                    // 4단계: 파싱
                    emitStep(sink, "문제 정보 파싱 중...");
                    AlgoProblemDto problem = parseAIProblemResponse(aiResponse, request);
                    List<AlgoTestcaseDto> testCases = parseAITestCaseResponse(aiResponse);

                    // 5단계: DB 저장
                    emitStep(sink, "데이터베이스에 저장 중...");

                    // 문제 저장 (MyBatis useGeneratedKeys로 ID 자동 설정)
                    algorithmProblemMapper.insertProblem(problem);
                    Long problemId = problem.getAlgoProblemId();

                    // 테스트케이스 저장
                    for (AlgoTestcaseDto tc : testCases) {
                        tc.setAlgoProblemId(problemId);
                        algorithmProblemMapper.insertTestcase(tc);
                    }

                    double generationTime = (System.currentTimeMillis() - startTime) / 1000.0;

                    // 6단계: 완료 이벤트 전송
                    Map<String, Object> completeData = new HashMap<>();
                    completeData.put("problemId", problemId);
                    completeData.put("title", problem.getAlgoProblemTitle());
                    completeData.put("description", problem.getAlgoProblemDescription());
                    completeData.put("difficulty", problem.getAlgoProblemDifficulty());
                    completeData.put("testCaseCount", testCases.size());
                    completeData.put("generationTime", generationTime);

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
     * ✅ 실제 OpenAI API 호출 (Spring AI 사용)
     */
    private String callOpenAI(String prompt) {
        try {
            log.debug("OpenAI API 호출 시작 - 프롬프트 길이: {}", prompt.length());

            // ChatClient 사용 (Spring AI 3.0+ 스타일)
            ChatClient chatClient = ChatClient.create(chatModel);

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.debug("OpenAI API 호출 완료 - 응답 길이: {}", response.length());

            return response;

        } catch (Exception e) {
            log.error("OpenAI API 호출 실패", e);
            throw new RuntimeException("AI 문제 생성 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 프롬프트 생성 (개선된 버전 - DATABASE 문제 지원 + 중복 방지)
     */
    private String buildPrompt(ProblemGenerationRequestDto request) {
        String difficultyDesc = getDifficultyDescription(request.getDifficulty());

        // 기존 문제 제목 목록 조회
        List<String> existingTitles = getExistingProblemTitles();
        String existingTitlesStr = existingTitles.isEmpty()
                ? ""
                : "\n\n## 중복 방지\n다음 문제 제목들과 중복되지 않도록 새로운 문제를 만들어주세요:\n"
                        + String.join(", ", existingTitles.stream().limit(20).toList());

        // DATABASE 문제인 경우
        if ("SQL".equalsIgnoreCase(request.getProblemType())) {
            return buildDatabasePrompt(difficultyDesc, request, existingTitlesStr);
        }

        // ALGORITHM 문제인 경우
        return buildAlgorithmPrompt(difficultyDesc, request, existingTitlesStr);
    }

    /**
     * 기존 문제 제목 목록 조회
     */
    private List<String> getExistingProblemTitles() {
        try {
            List<AlgoProblemDto> problems = algorithmProblemMapper.selectProblems(0, 100); // 최근 100개 문제
            return problems.stream()
                    .map(AlgoProblemDto::getAlgoProblemTitle)
                    .filter(title -> title != null && !title.isEmpty())
                    .toList();
        } catch (Exception e) {
            log.warn("기존 문제 제목 조회 실패, 빈 목록 반환", e);
            return List.of();
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
     * ALGORITHM 문제 프롬프트 생성
     */
    private String buildAlgorithmPrompt(String difficultyDesc, ProblemGenerationRequestDto request,
            String existingTitlesStr) {
        return String.format("""
                당신은 알고리즘 문제 출제 전문가입니다.
                다음 조건에 맞는 알고리즘 문제를 **반드시 JSON 형식으로만** 생성해주세요.

                ## 요구사항
                - 난이도: %s
                - 주제: %s
                %s
                %s

                ## 중요 규칙
                1. 문제는 실제 코딩 테스트 수준으로 작성
                2. 테스트케이스는 최소 3개 이상 포함
                3. 입출력 예제는 명확하게 작성
                4. **JSON 형식 외 다른 텍스트 절대 포함 금지**

                ## 응답 형식 (반드시 이 JSON 구조로만 응답)
                {
                  "title": "문제 제목",
                  "description": "문제 설명 (자세하게)",
                  "constraints": "제약 조건",
                  "inputFormat": "입력 형식 설명",
                  "outputFormat": "출력 형식 설명",
                  "sampleInput": "1 2",
                  "sampleOutput": "3",
                  "testCases": [
                    {"input": "3 4", "output": "7"},
                    {"input": "5 7", "output": "12"},
                    {"input": "10 20", "output": "30"}
                  ]
                }

                **주의**: JSON만 출력하고 다른 설명은 절대 포함하지 마세요!
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

    /**
     * AI 응답 JSON 정제
     * - 마크다운 코드 블록 제거
     * - 유효하지 않은 JSON 이스케이프 시퀀스 처리 (정규식 패턴 등)
     */
    private String sanitizeJsonResponse(String aiResponse) {
        // null 또는 빈 문자열 체크
        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            log.warn("AI 응답이 null이거나 비어있습니다.");
            return "{}";
        }

        // 1. 마크다운 코드 블록 제거
        String cleanedJson = aiResponse
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*$", "")
                .replaceAll("```", "")  // 추가: 남은 ``` 제거
                .trim();

        if (cleanedJson.isEmpty()) {
            log.warn("마크다운 제거 후 JSON이 비어있습니다.");
            return "{}";
        }

        // 2. 유효하지 않은 JSON 이스케이프 시퀀스를 이중 백슬래시로 변환
        // JSON 유효 이스케이프: 큰따옴표, 백슬래시, 슬래시, b, f, n, r, t, 유니코드(u+4자리hex)
        // 그 외 정규식 패턴(w, d, s 등)은 유효하지 않으므로 백슬래시를 이중으로 변환
        StringBuilder result = new StringBuilder();
        int i = 0;
        int escapesFixed = 0;

        while (i < cleanedJson.length()) {
            char c = cleanedJson.charAt(i);

            if (c == '\\') {
                // 다음 문자가 있는지 확인
                if (i + 1 < cleanedJson.length()) {
                    char next = cleanedJson.charAt(i + 1);

                    // 유효한 JSON 이스케이프 시퀀스 확인
                    if (next == '"' || next == '\\' || next == '/' ||
                        next == 'b' || next == 'f' || next == 'n' ||
                        next == 'r' || next == 't') {
                        // 유효한 이스케이프 - 그대로 유지
                        result.append(c);
                        result.append(next);
                        i += 2;
                    } else if (next == 'u' && i + 5 < cleanedJson.length()) {
                        // \ uXXXX 유니코드 이스케이프 확인
                        String hex = cleanedJson.substring(i + 2, i + 6);
                        if (hex.matches("[0-9a-fA-F]{4}")) {
                            result.append(cleanedJson, i, i + 6);
                            i += 6;
                        } else {
                            // 유효하지 않은 유니코드 - 백슬래시 이스케이프
                            result.append("\\\\");
                            escapesFixed++;
                            i += 1;
                        }
                    } else {
                        // 유효하지 않은 이스케이프 - 백슬래시를 이중으로
                        result.append("\\\\");
                        escapesFixed++;
                        i += 1;
                    }
                } else {
                    // 문자열 끝에 단독 백슬래시 - 이스케이프 처리
                    result.append("\\\\");
                    escapesFixed++;
                    i += 1;
                }
            } else {
                result.append(c);
                i += 1;
            }
        }

        if (escapesFixed > 0) {
            log.info("JSON 이스케이프 시퀀스 {} 개 수정됨", escapesFixed);
        }

        return result.toString();
    }

    /**
     * 문제 정보 파싱
     */
    private AlgoProblemDto parseAIProblemResponse(String aiResponse, ProblemGenerationRequestDto request)
            throws JsonProcessingException {

        // JSON 전처리 (```json ``` 제거 + 유효하지 않은 이스케이프 시퀀스 처리)
        String cleanedJson = sanitizeJsonResponse(aiResponse);

        JsonNode jsonNode = objectMapper.readTree(cleanedJson);

        // problemType 결정
        ProblemType problemType = "SQL".equalsIgnoreCase(request.getProblemType())
                ? ProblemType.SQL
                : ProblemType.ALGORITHM;

        // initScript 파싱 (DATABASE 문제인 경우)
        String initScript = null;
        if (problemType == ProblemType.SQL) {
            JsonNode initScriptNode = jsonNode.get("initScript");
            if (initScriptNode != null && !initScriptNode.isNull()) {
                initScript = initScriptNode.asText();
            }
        }

        return AlgoProblemDto.builder()
                .algoProblemTitle(jsonNode.get("title").asText())
                .algoProblemDescription(buildFullDescription(jsonNode))
                .algoProblemDifficulty(request.getDifficulty())
                .algoProblemSource(ProblemSource.AI_GENERATED)
                .problemType(problemType)
                .initScript(initScript) // DATABASE 문제인 경우 초기화 스크립트 설정
                .timelimit(request.getTimeLimit() != null
                        ? request.getTimeLimit()
                        : getDefaultTimeLimit(request.getDifficulty()))
                .memorylimit(request.getMemoryLimit())
                .algoProblemTags(buildTagsJson(request.getTopic()))
                .algoProblemStatus(true)
                .algoCreatedAt(LocalDateTime.now())
                .algoUpdatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 태그를 JSON 배열 형식으로 변환
     */
    private String buildTagsJson(String topic) {
        if (topic == null || topic.trim().isEmpty()) {
            return "[]"; // 빈 JSON 배열
        }

        try {
            // 쉼표로 구분된 태그를 JSON 배열로 변환
            String[] tags = topic.split(",");
            List<String> tagList = new ArrayList<>();

            for (String tag : tags) {
                String trimmed = tag.trim();
                if (!trimmed.isEmpty()) {
                    tagList.add(trimmed);
                }
            }

            // ObjectMapper로 JSON 배열 생성
            return objectMapper.writeValueAsString(tagList);

        } catch (Exception e) {
            log.error("태그 JSON 변환 실패, 기본값 사용: {}", topic, e);
            // 실패 시 단일 태그로 JSON 배열 생성
            return "[\"" + topic.replace("\"", "\\\"") + "\"]";
        }
    }

    /**
     * 문제 설명 전체 구성
     */
    private String buildFullDescription(JsonNode jsonNode) {
        return String.format("""
                %s

                **입력**
                %s

                **출력**
                %s

                **제한 사항**
                %s

                **예제 입력**
                %s

                **예제 출력**
                %s
                """,
                jsonNode.get("description").asText(),
                jsonNode.get("inputFormat").asText(),
                jsonNode.get("outputFormat").asText(),
                jsonNode.get("constraints").asText(),
                jsonNode.get("sampleInput").asText(),
                jsonNode.get("sampleOutput").asText());
    }

    /**
     * 테스트케이스 파싱
     */
    private List<AlgoTestcaseDto> parseAITestCaseResponse(String aiResponse) throws JsonProcessingException {

        // JSON 전처리 (유효하지 않은 이스케이프 시퀀스 처리 포함)
        String cleanedJson = sanitizeJsonResponse(aiResponse);

        JsonNode jsonNode = objectMapper.readTree(cleanedJson);
        JsonNode testCasesNode = jsonNode.get("testCases");

        List<AlgoTestcaseDto> testCases = new ArrayList<>();

        // 샘플 테스트케이스 (첫 번째)
        testCases.add(AlgoTestcaseDto.builder()
                .inputData(jsonNode.get("sampleInput").asText())
                .expectedOutput(jsonNode.get("sampleOutput").asText())
                .isSample(true)
                .build());

        // 히든 테스트케이스
        if (testCasesNode != null && testCasesNode.isArray()) {
            for (JsonNode testCase : testCasesNode) {
                testCases.add(
                        AlgoTestcaseDto.builder()
                                .inputData(testCase.get("input").asText())
                                .expectedOutput(testCase.get("output").asText())
                                .isSample(false)
                                .build());
            }
        }

        return testCases;
    }

    /**
     * 기본 시간 제한
     */
    private Integer getDefaultTimeLimit(ProblemDifficulty difficulty) {
        return switch (difficulty) {
            case BRONZE -> 1000;
            case SILVER -> 2000;
            case GOLD -> 3000;
            case PLATINUM -> 5000;
        };
    }
}