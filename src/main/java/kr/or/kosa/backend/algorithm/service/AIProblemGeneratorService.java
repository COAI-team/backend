package kr.or.kosa.backend.algorithm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.algorithm.domain.AlgoProblem;
import kr.or.kosa.backend.algorithm.domain.AlgoTestcase;
import kr.or.kosa.backend.algorithm.domain.ProblemDifficulty;
import kr.or.kosa.backend.algorithm.domain.ProblemSource;
import kr.or.kosa.backend.algorithm.dto.ProblemGenerationRequestDto;
import kr.or.kosa.backend.algorithm.dto.ProblemGenerationResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 기반 알고리즘 문제 생성 서비스
 * WebClient를 사용하여 외부 AI API와 통신
 */
@Service
@Slf4j
public class AIProblemGeneratorService {

    // Spring이 자동으로 주입해주는 Bean들
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    // OpenAI API 설정 (application.yml에서 주입)
    @Value("${openai.api.key}")
    private String openaiApiKey;

    @Value("${openai.api.base-url}")
    private String openaiApiUrl;

    @Value("${openai.api.model}")
    private String openaiModel;

    @Value("${openai.api.max-tokens:2000}")
    private Integer maxTokens;

    // 생성자 주입
    @Autowired
    public AIProblemGeneratorService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * AI를 사용하여 알고리즘 문제 생성
     * @param request 생성 요청 정보
     * @return 생성된 문제 정보
     */
    public ProblemGenerationResponseDto generateProblem(ProblemGenerationRequestDto request) {

        long startTime = System.currentTimeMillis();

        try {
            log.info("AI 문제 생성 시작 - 난이도: {}, 주제: {}", request.getDifficulty(), request.getTopic());

            // 1. AI 프롬프트 생성
            String prompt = buildPrompt(request);

            // 2. AI API 호출 (현재는 Mock 데이터)
            String aiResponse = callAIService(prompt);

            // 3. AI 응답 파싱
            AlgoProblem problem = parseAIProblemResponse(aiResponse, request);
            List<AlgoTestcase> testCases = parseAITestCaseResponse(aiResponse);

            // 4. 생성 시간 계산
            double generationTime = (System.currentTimeMillis() - startTime) / 1000.0;

            log.info("AI 문제 생성 완료 - 소요시간: {}초", generationTime);

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
     * AI 프롬프트 생성
     */
    private String buildPrompt(ProblemGenerationRequestDto request) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("알고리즘 문제를 생성해주세요.\n\n");
        prompt.append("**요구사항:**\n");
        prompt.append("- 난이도: ").append(getDifficultyDescription(request.getDifficulty())).append("\n");
        prompt.append("- 주제: ").append(request.getTopic()).append("\n");
        prompt.append("- 언어: ").append(request.getLanguage()).append("\n");

        if (request.getAdditionalRequirements() != null) {
            prompt.append("- 추가 요구사항: ").append(request.getAdditionalRequirements()).append("\n");
        }

        prompt.append("\n**응답 형식:**\n");
        prompt.append("JSON 형태로 다음 필드를 포함해주세요:\n");
        prompt.append("{\n");
        prompt.append("  \"title\": \"문제 제목\",\n");
        prompt.append("  \"description\": \"문제 설명 (자세히)\",\n");
        prompt.append("  \"constraints\": \"제약 조건\",\n");
        prompt.append("  \"inputFormat\": \"입력 형식\",\n");
        prompt.append("  \"outputFormat\": \"출력 형식\",\n");
        prompt.append("  \"sampleInput\": \"샘플 입력\",\n");
        prompt.append("  \"sampleOutput\": \"샘플 출력\",\n");
        prompt.append("  \"testCases\": [\n");
        prompt.append("    {\"input\": \"테스트 입력\", \"output\": \"예상 출력\"},\n");
        prompt.append("    ...\n");
        prompt.append("  ]\n");
        prompt.append("}\n");

        return prompt.toString();
    }

    /**
     * 난이도별 설명 반환
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
     * AI 서비스 호출 (현재는 Mock)
     * 실제로는 OpenAI API나 다른 AI 서비스 호출
     */
    private String callAIService(String prompt) {
        log.debug("AI API 호출 - 프롬프트 길이: {} 문자", prompt.length());

        // TODO: 실제 AI API 호출 구현
        // 현재는 Mock 데이터 반환
        return createMockAIResponse();
    }

    /**
     * Mock AI 응답 생성 (테스트용)
     */
    private String createMockAIResponse() {
        return """
            {
              "title": "두 수의 합",
              "description": "두 정수 A와 B를 입력받은 다음, A+B를 출력하는 프로그램을 작성하시오.",
              "constraints": "첫째 줄에 A와 B가 주어진다. (0 < A, B < 10)",
              "inputFormat": "첫째 줄에 A와 B가 주어진다.",
              "outputFormat": "첫째 줄에 A+B를 출력한다.",
              "sampleInput": "1 2",
              "sampleOutput": "3",
              "testCases": [
                {"input": "1 2", "output": "3"},
                {"input": "3 4", "output": "7"},
                {"input": "5 5", "output": "10"},
                {"input": "9 1", "output": "10"},
                {"input": "0 0", "output": "0"}
              ]
            }
            """;
    }

    /**
     * AI 응답에서 문제 정보 파싱
     */
    private AlgoProblem parseAIProblemResponse(String aiResponse, ProblemGenerationRequestDto request)
            throws JsonProcessingException {

        JsonNode jsonNode = objectMapper.readTree(aiResponse);

        return AlgoProblem.builder()
                .algoProblemTitle(jsonNode.get("title").asText())
                .algoProblemDescription(buildFullDescription(jsonNode))
                .algoProblemDifficulty(request.getDifficulty())
                .algoProblemSource(ProblemSource.AI_GENERATED)
                .language(request.getLanguage())
                .timelimit(request.getTimeLimit() != null ? request.getTimeLimit() : getDefaultTimeLimit(request.getDifficulty()))
                .memorylimit(request.getMemoryLimit())
                .algoProblemTags(request.getTopic())
                .algoProblemStatus(true)
                .algoCreatedAt(LocalDateTime.now())
                .algoUpdatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 전체 문제 설명 구성
     */
    private String buildFullDescription(JsonNode jsonNode) {
        StringBuilder description = new StringBuilder();

        description.append(jsonNode.get("description").asText()).append("\n\n");
        description.append("**입력**\n");
        description.append(jsonNode.get("inputFormat").asText()).append("\n\n");
        description.append("**출력**\n");
        description.append(jsonNode.get("outputFormat").asText()).append("\n\n");
        description.append("**제한 사항**\n");
        description.append(jsonNode.get("constraints").asText()).append("\n\n");
        description.append("**예제 입력**\n");
        description.append(jsonNode.get("sampleInput").asText()).append("\n\n");
        description.append("**예제 출력**\n");
        description.append(jsonNode.get("sampleOutput").asText()).append("\n");

        return description.toString();
    }

    /**
     * AI 응답에서 테스트케이스 파싱
     */
    private List<AlgoTestcase> parseAITestCaseResponse(String aiResponse) throws JsonProcessingException {

        JsonNode jsonNode = objectMapper.readTree(aiResponse);
        JsonNode testCasesNode = jsonNode.get("testCases");

        List<AlgoTestcase> testCases = new ArrayList<>();

        // 샘플 테스트케이스 (첫 번째)
        testCases.add(AlgoTestcase.builder()
                .inputData(jsonNode.get("sampleInput").asText())
                .expectedOutput(jsonNode.get("sampleOutput").asText())
                .isSample(true)
                .build());

        // 히든 테스트케이스들
        if (testCasesNode != null && testCasesNode.isArray()) {
            for (JsonNode testCase : testCasesNode) {
                testCases.add(AlgoTestcase.builder()
                        .inputData(testCase.get("input").asText())
                        .expectedOutput(testCase.get("output").asText())
                        .isSample(false)
                        .build());
            }
        }

        return testCases;
    }

    /**
     * 난이도별 기본 시간 제한 반환
     */
    private Integer getDefaultTimeLimit(ProblemDifficulty difficulty) {
        return switch (difficulty) {
            case BRONZE -> 1000;   // 1초
            case SILVER -> 2000;   // 2초
            case GOLD -> 3000;     // 3초
            case PLATINUM -> 5000; // 5초
        };
    }
}