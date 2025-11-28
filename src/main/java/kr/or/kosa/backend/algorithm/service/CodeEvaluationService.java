package kr.or.kosa.backend.algorithm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import kr.or.kosa.backend.algorithm.dto.AICodeEvaluationResult;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodeEvaluationService {

    private final OpenAiChatModel chatModel; // ✔ AIProblemGeneratorService와 동일하게 통일

    @Async("aiEvaluationExecutor")
    public CompletableFuture<AICodeEvaluationResult> evaluateCode(
            String sourceCode,
            String problemDescription,
            String language,
            String judgeResult
    ) {
        try {
            log.info("AI 코드 평가 요청 시작 - language: {}, judgeResult: {}", language, judgeResult);

            // 1) 시스템 프롬프트 / 사용자 프롬프트 생성
            String systemPrompt = createSystemPrompt(language, judgeResult);
            String userPrompt = createUserPrompt(sourceCode, problemDescription);

            // 2) Spring AI 최신 ChatClient 사용 방식
            ChatClient chatClient = ChatClient.create(chatModel);

            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();          // ✔ 이제 getContent() 대신 content()

            // 3) JSON 파싱
            AICodeEvaluationResult result = parseAIResponse(response);

            log.info("AI 코드 평가 완료 - 점수 {}", result.getAiScore());
            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("AI 코드 평가 실패", e);

            return CompletableFuture.completedFuture(
                    AICodeEvaluationResult.builder()
                            .aiScore(50.0)
                            .feedback("AI 평가 도중 오류 발생: " + e.getMessage())
                            .codeQuality("FAIR")
                            .efficiency("UNKNOWN")
                            .readability("UNKNOWN")
                            .improvementTips(List.of("코드를 더 명확하게 작성해보세요."))
                            .build()
            );
        }
    }

    // --------------------------
    // ✨ 아래 부분은 기존 코드 그대로 사용해도 됨
    // --------------------------

    private String createSystemPrompt(String language, String judgeResult) {
        return String.format("""
            당신은 숙련된 프로그래밍 코드 리뷰어입니다.
            - 분석 언어: %s
            - Judge0 결과: %s

            출력 형식(JSON):
            {
              "aiScore": 85,
              "feedback": "요약",
              "codeQuality": "GOOD",
              "efficiency": "FAIR",
              "readability": "GOOD",
              "improvementTips": ["...", "..."]
            }

            JSON만 출력하세요.
            """, language, judgeResult);
    }

    private String createUserPrompt(String sourceCode, String problemDescription) {
        return String.format("""
            문제 설명:
            %s

            사용자 코드:
            %s
            """, problemDescription, sourceCode);
    }

    private AICodeEvaluationResult parseAIResponse(String json) {
        json = json.replaceAll("```json|```", "").trim();

        double score = extractJsonDouble(json, "aiScore", 70);
        String feedback = extractJsonString(json, "feedback", "AI 응답 파싱 실패");
        String codeQuality = extractJsonString(json, "codeQuality", "FAIR");
        String efficiency = extractJsonString(json, "efficiency", "FAIR");
        String readability = extractJsonString(json, "readability", "FAIR");

        return AICodeEvaluationResult.builder()
                .aiScore(score)
                .feedback(feedback)
                .codeQuality(codeQuality)
                .efficiency(efficiency)
                .readability(readability)
                .improvementTips(List.of("개선 필요"))
                .build();
    }

    private double extractJsonDouble(String json, String key, double defaultValue) {
        try {
            var matcher = java.util.regex.Pattern
                    .compile("\"" + key + "\"\\s*:\\s*([0-9.]+)")
                    .matcher(json);
            if (matcher.find()) return Double.parseDouble(matcher.group(1));
        } catch (Exception ignored) {}
        return defaultValue;
    }

    private String extractJsonString(String json, String key, String defaultValue) {
        try {
            var matcher = java.util.regex.Pattern
                    .compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"")
                    .matcher(json);
            if (matcher.find()) return matcher.group(1);
        } catch (Exception ignored) {}
        return defaultValue;
    }
}
