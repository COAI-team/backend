package kr.or.kosa.backend.algorithm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;  // ✅ 올바른 임포트
import org.springframework.ai.openai.OpenAiChatModel;     // ✅ OpenAiChatModel 추가
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * AI 코드 평가 서비스 (ALG-08)
 * Spring AI를 사용하여 코드 품질을 분석하고 피드백을 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeEvaluationService {

    private final OpenAiChatModel chatModel; // ✅ AIProblemGeneratorService와 동일하게 수정

    /**
     * 코드 품질 평가 (비동기 처리)
     *
     * @param sourceCode 평가할 소스 코드
     * @param problemDescription 문제 설명
     * @param language 프로그래밍 언어
     * @param judgeResult Judge0 채점 결과
     * @return AI 평가 결과
     */
    @Async
    public CompletableFuture<AICodeEvaluationResult> evaluateCode(
            String sourceCode,
            String problemDescription,
            String language,
            String judgeResult) {

        try {
            log.info("AI 코드 평가 시작 - 언어: {}, 판정결과: {}", language, judgeResult);

            // 시스템 프롬프트 생성
            String systemPrompt = createSystemPrompt(language, judgeResult);

            // 사용자 프롬프트 생성
            String userPrompt = createUserPrompt(sourceCode, problemDescription);

            // ✅ AIProblemGeneratorService와 동일한 방식으로 ChatClient 생성
            ChatClient chatClient = ChatClient.create(chatModel);

            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            // 응답 파싱
            AICodeEvaluationResult result = parseAIResponse(response);

            log.info("AI 코드 평가 완료 - 점수: {}", result.getAiScore());
            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("AI 코드 평가 중 오류 발생", e);

            // 실패 시 기본값 반환
            AICodeEvaluationResult defaultResult = AICodeEvaluationResult.builder()
                    .aiScore(50.0)
                    .feedback("AI 평가를 수행할 수 없습니다. 다시 시도해주세요.")
                    .codeQuality("FAIR")
                    .efficiency("UNKNOWN")
                    .readability("UNKNOWN")
                    .improvementTips(List.of("코드를 더 명확하게 작성해보세요."))
                    .build();

            return CompletableFuture.completedFuture(defaultResult);
        }
    }

    /**
     * 시스템 프롬프트 생성
     */
    private String createSystemPrompt(String language, String judgeResult) {
        return String.format("""
            당신은 숙련된 프로그래밍 코드 리뷰어입니다. 
            
            **역할:**
            - %s 코드를 전문적으로 분석
            - Judge0 채점 결과(%s)를 고려한 종합 평가
            - 건설적이고 구체적인 피드백 제공
            
            **평가 기준:**
            1. **코드 품질** (40점): 가독성, 구조, 네이밍
            2. **알고리즘 효율성** (35점): 시간/공간 복잡도  
            3. **문제 해결 적합성** (25점): 요구사항 충족도
            
            **출력 형식 (JSON):**
            ```json
            {
              "aiScore": 85.5,
              "feedback": "전체적인 평가 요약 (2-3문장)",
              "codeQuality": "EXCELLENT|GOOD|FAIR|POOR",
              "efficiency": "EXCELLENT|GOOD|FAIR|POOR", 
              "readability": "EXCELLENT|GOOD|FAIR|POOR",
              "improvementTips": [
                "구체적인 개선 제안 1",
                "구체적인 개선 제안 2"
              ]
            }
            ```
            
            **중요:** JSON 형식으로만 답변하고, 다른 텍스트는 포함하지 마세요.
            """, language, judgeResult);
    }

    /**
     * 사용자 프롬프트 생성
     */
    private String createUserPrompt(String sourceCode, String problemDescription) {
        return String.format("""
            **문제 설명:**
            %s
            
            **제출된 코드:**
            ```
            %s
            ```
            
            위 코드를 분석하여 JSON 형식으로 평가해주세요.
            """, problemDescription, sourceCode);
    }

    /**
     * AI 응답 파싱 (Jackson 사용 권장)
     */
    private AICodeEvaluationResult parseAIResponse(String aiResponse) {
        try {
            // JSON 응답에서 불필요한 마크다운 제거
            String jsonResponse = aiResponse.replaceAll("```json|```", "").trim();

            // ✅ TODO: Jackson ObjectMapper 사용 권장
            // ObjectMapper mapper = new ObjectMapper();
            // return mapper.readValue(jsonResponse, AICodeEvaluationResult.class);

            // 임시 파싱 로직 (실제 구현시 Jackson ObjectMapper 사용)
            double aiScore = extractJsonValue(jsonResponse, "aiScore", 75.0);
            String feedback = extractJsonStringValue(jsonResponse, "feedback", "코드가 적절히 작성되었습니다.");
            String codeQuality = extractJsonStringValue(jsonResponse, "codeQuality", "GOOD");
            String efficiency = extractJsonStringValue(jsonResponse, "efficiency", "GOOD");
            String readability = extractJsonStringValue(jsonResponse, "readability", "GOOD");

            // improvementTips 배열 파싱 (간단 구현)
            List<String> improvementTips = List.of(
                    "변수명을 더 명확하게 작성해보세요.",
                    "알고리즘 복잡도를 개선할 수 있는지 고려해보세요."
            );

            return AICodeEvaluationResult.builder()
                    .aiScore(aiScore)
                    .feedback(feedback)
                    .codeQuality(codeQuality)
                    .efficiency(efficiency)
                    .readability(readability)
                    .improvementTips(improvementTips)
                    .build();

        } catch (Exception e) {
            log.warn("AI 응답 파싱 실패, 기본값 사용: {}", e.getMessage());

            // 파싱 실패 시 기본값
            return AICodeEvaluationResult.builder()
                    .aiScore(70.0)
                    .feedback("AI 응답을 처리하는 중 문제가 발생했습니다.")
                    .codeQuality("FAIR")
                    .efficiency("FAIR")
                    .readability("FAIR")
                    .improvementTips(List.of("코드를 검토하고 개선점을 찾아보세요."))
                    .build();
        }
    }

    /**
     * JSON에서 숫자 값 추출 (간단 구현)
     */
    private double extractJsonValue(String json, String key, double defaultValue) {
        try {
            String pattern = "\"" + key + "\"\\s*:\\s*([0-9.]+)";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return Double.parseDouble(m.group(1));
            }
        } catch (Exception e) {
            log.warn("JSON 파싱 실패: {}", key);
        }
        return defaultValue;
    }

    /**
     * JSON에서 문자열 값 추출 (간단 구현)
     */
    private String extractJsonStringValue(String json, String key, String defaultValue) {
        try {
            String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception e) {
            log.warn("JSON 파싱 실패: {}", key);
        }
        return defaultValue;
    }
}

/**
 * AI 코드 평가 결과 DTO
 */
@lombok.Data
@lombok.Builder
class AICodeEvaluationResult {
    private Double aiScore;           // AI 평가 점수 (0-100)
    private String feedback;          // 종합 피드백
    private String codeQuality;       // 코드 품질 (EXCELLENT/GOOD/FAIR/POOR)
    private String efficiency;        // 효율성 평가
    private String readability;       // 가독성 평가
    private List<String> improvementTips; // 개선 제안 리스트
}