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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class AIProblemGeneratorService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    /**
     * application.yml에서 불러올 실제 키 위치
     *
     * spring:
     *   ai:
     *     openai:
     *       api-key: ${OPENAI_API_KEY}
     *       chat:
     *         options:
     *           model: ${OPENAI_MODEL:gpt-4o}
     *           max-tokens: 2000
     */

    @Value("${spring.ai.openai.api-key}")
    private String openaiApiKey;

    @Value("${spring.ai.openai.chat.options.model}")
    private String openaiModel;

    @Value("${spring.ai.openai.chat.options.max-tokens:2000}")
    private Integer maxTokens;

    @Value("${spring.ai.openai.base-url:https://api.openai.com/v1/chat/completions}")
    private String openaiApiUrl;

    public AIProblemGeneratorService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * AI 문제 생성
     */
    public ProblemGenerationResponseDto generateProblem(ProblemGenerationRequestDto request) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("AI 문제 생성 시작 - 난이도: {}, 주제: {}", request.getDifficulty(), request.getTopic());

            String prompt = buildPrompt(request);

            // TODO: 실제 OpenAI API 호출로 교체 가능
            String aiResponse = callAIService(prompt);

            AlgoProblem problem = parseAIProblemResponse(aiResponse, request);
            List<AlgoTestcase> testCases = parseAITestCaseResponse(aiResponse);

            double generationTime = (System.currentTimeMillis() - startTime) / 1000.0;

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
     * 프롬프트 생성
     */
    private String buildPrompt(ProblemGenerationRequestDto request) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("알고리즘 문제를 생성해주세요.\n\n")
                .append("**요구사항:**\n")
                .append("- 난이도: ").append(getDifficultyDescription(request.getDifficulty())).append("\n")
                .append("- 주제: ").append(request.getTopic()).append("\n")
                .append("- 언제: ").append(request.getLanguage()).append("\n");

        if (request.getAdditionalRequirements() != null) {
            prompt.append("- 추가 요구사항: ").append(request.getAdditionalRequirements()).append("\n");
        }

        prompt.append("\n**응답 형식:**\n")
                .append("{\n")
                .append("  \"title\": \"문제 제목\",\n")
                .append("  \"description\": \"문제 설명\",\n")
                .append("  \"constraints\": \"제약 조건\",\n")
                .append("  \"inputFormat\": \"입력 형식\",\n")
                .append("  \"outputFormat\": \"출력 형식\",\n")
                .append("  \"sampleInput\": \"샘플 입력\",\n")
                .append("  \"sampleOutput\": \"샘플 출력\",\n")
                .append("  \"testCases\": [ {\"input\": \"...\", \"output\": \"...\"} ]\n")
                .append("}");

        return prompt.toString();
    }

    private String getDifficultyDescription(ProblemDifficulty difficulty) {
        return switch (difficulty) {
            case BRONZE -> "초급 (기본 문법, 간단한 구현)";
            case SILVER -> "초중급 (기본 알고리즘, 자료구조)";
            case GOLD -> "중급 (고급 알고리즘, 최적화)";
            case PLATINUM -> "고급 (복잡한 알고리즘, 수학적 사고)";
        };
    }

    /**
     * 현재는 Mock → 실제 API로 교체 가능
     */
    private String callAIService(String prompt) {
        log.debug("AI API 호출 - 프롬프트 길이: {}", prompt.length());
        return createMockAIResponse();
    }

    /**
     * Mock Response
     */
    private String createMockAIResponse() {
        return """
            {
              "title": "두 수의 합",
              "description": "두 정수 A와 B의 합을 구하시오.",
              "constraints": "0 < A, B < 10",
              "inputFormat": "정수 A B",
              "outputFormat": "A+B",
              "sampleInput": "1 2",
              "sampleOutput": "3",
              "testCases": [
                {"input": "3 4", "output": "7"},
                {"input": "5 7", "output": "12"}
              ]
            }
        """;
    }

    /**
     * 문제 정보 파싱
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

    private String buildFullDescription(JsonNode jsonNode) {
        return """
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
                """.formatted(
                jsonNode.get("description").asText(),
                jsonNode.get("inputFormat").asText(),
                jsonNode.get("outputFormat").asText(),
                jsonNode.get("constraints").asText(),
                jsonNode.get("sampleInput").asText(),
                jsonNode.get("sampleOutput").asText()
        );
    }

    /**
     * 테스트케이스 파싱
     */
    private List<AlgoTestcase> parseAITestCaseResponse(String aiResponse) throws JsonProcessingException {

        JsonNode jsonNode = objectMapper.readTree(aiResponse);
        JsonNode testCasesNode = jsonNode.get("testCases");

        List<AlgoTestcase> testCases = new ArrayList<>();

        // 샘플
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
                                .build()
                );
            }
        }

        return testCases;
    }

    private Integer getDefaultTimeLimit(ProblemDifficulty difficulty) {
        return switch (difficulty) {
            case BRONZE -> 1000;
            case SILVER -> 2000;
            case GOLD -> 3000;
            case PLATINUM -> 5000;
        };
    }
}
