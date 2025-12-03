package kr.or.kosa.backend.algorithm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.algorithm.domain.AlgoProblem;
import kr.or.kosa.backend.algorithm.domain.AlgoTestcase;
import kr.or.kosa.backend.algorithm.domain.ProblemDifficulty;
import kr.or.kosa.backend.algorithm.domain.ProblemSource;
import kr.or.kosa.backend.algorithm.domain.ProblemType;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
            AlgoProblem problem = parseAIProblemResponse(aiResponse, request);
            List<AlgoTestcase> testCases = parseAITestCaseResponse(aiResponse);

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
            List<AlgoProblem> problems = algorithmProblemMapper.selectProblems(0, 100); // 최근 100개 문제
            return problems.stream()
                    .map(AlgoProblem::getAlgoProblemTitle)
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
     * 문제 정보 파싱
     */
    private AlgoProblem parseAIProblemResponse(String aiResponse, ProblemGenerationRequestDto request)
            throws JsonProcessingException {

        // JSON 전처리 (```json ``` 제거)
        String cleanedJson = aiResponse
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*$", "")
                .trim();

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

        return AlgoProblem.builder()
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
    private List<AlgoTestcase> parseAITestCaseResponse(String aiResponse) throws JsonProcessingException {

        // JSON 전처리
        String cleanedJson = aiResponse
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*$", "")
                .trim();

        JsonNode jsonNode = objectMapper.readTree(cleanedJson);
        JsonNode testCasesNode = jsonNode.get("testCases");

        List<AlgoTestcase> testCases = new ArrayList<>();

        // 샘플 테스트케이스 (첫 번째)
        testCases.add(AlgoTestcase.builder()
                .inputData(jsonNode.get("sampleInput").asText())
                .expectedOutput(jsonNode.get("sampleOutput").asText())
                .isSample(true)
                .build());

        // 히든 테스트케이스
        if (testCasesNode != null && testCasesNode.isArray()) {
            for (JsonNode testCase : testCasesNode) {
                testCases.add(
                        AlgoTestcase.builder()
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