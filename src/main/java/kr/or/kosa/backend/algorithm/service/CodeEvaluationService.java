package kr.or.kosa.backend.algorithm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import kr.or.kosa.backend.algorithm.dto.AICodeEvaluationResult;

/**
 * AI 코드 평가 서비스
 *
 * 평가 기준 체계 (논문 및 산업 자료 기반):
 * 1. 접근법 적합성 (35%): 문제 토픽과 사용한 알고리즘 일치 여부
 * 2. 효율성 (35%): 기대 복잡도 대비 실제 복잡도 상대 평가
 * 3. 코드 품질 (30%): 가독성, 명명 규칙, 구조화
 *
 * 참고 자료:
 * - Buse & Weimer (2010) "Learning a Metric for Code Readability" IEEE TSE
 * - Tech Interview Handbook - Coding Interview Rubrics
 * - Robert C. Martin "Clean Code"
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CodeEvaluationService {

    private final OpenAiChatModel chatModel;

    /**
     * AI 코드 평가 실행 (고도화된 프롬프트 버전)
     *
     * @param sourceCode 사용자 제출 코드
     * @param problemDescription 문제 설명
     * @param language 프로그래밍 언어
     * @param judgeResult Judge0 채점 결과 (AC/WA/TLE/RE/CE)
     * @param problemTopic 문제 토픽/태그 (예: "dp", "greedy", "bfs")
     * @param expectedTimeComplexity 문제의 기대 시간 복잡도 (예: "O(n log n)")
     * @param passedCount 통과한 테스트케이스 수
     * @param totalCount 전체 테스트케이스 수
     */
    @Async("aiEvaluationExecutor")
    public CompletableFuture<AICodeEvaluationResult> evaluateCode(
            String sourceCode,
            String problemDescription,
            String language,
            String judgeResult,
            String problemTopic,
            String expectedTimeComplexity,
            int passedCount,
            int totalCount
    ) {
        try {
            log.info("AI 코드 평가 요청 시작 - language: {}, judgeResult: {}, topic: {}",
                    language, judgeResult, problemTopic);

            // 1) 고도화된 시스템 프롬프트 생성
            String systemPrompt = createEnhancedSystemPrompt(
                    language, judgeResult, problemTopic, expectedTimeComplexity, passedCount, totalCount
            );

            // 2) 사용자 프롬프트 생성
            String userPrompt = createEnhancedUserPrompt(sourceCode, problemDescription);

            // 3) Spring AI ChatClient 호출
            ChatClient chatClient = ChatClient.create(chatModel);

            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            log.debug("AI 응답 원본: {}", response);

            // 4) JSON 파싱
            AICodeEvaluationResult result = parseEnhancedAIResponse(response);

            log.info("AI 코드 평가 완료 - 점수: {}, 효율성: {}", result.getAiScore(), result.getEfficiency());
            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("AI 코드 평가 실패", e);
            return CompletableFuture.completedFuture(createFallbackResult(e.getMessage()));
        }
    }

    /**
     * 기존 메서드 호환성 유지 (파라미터 적은 버전)
     */
    @Async("aiEvaluationExecutor")
    public CompletableFuture<AICodeEvaluationResult> evaluateCode(
            String sourceCode,
            String problemDescription,
            String language,
            String judgeResult
    ) {
        return evaluateCode(
                sourceCode, problemDescription, language, judgeResult,
                "algorithm", // 기본 토픽
                null,        // 기대 복잡도 미지정 시 LLM이 추론
                0, 0         // 테스트케이스 정보 없음
        );
    }

    /**
     * 고도화된 시스템 프롬프트 생성
     * 평가 기준 체계를 명확히 지시
     */
    private String createEnhancedSystemPrompt(
            String language,
            String judgeResult,
            String problemTopic,
            String expectedTimeComplexity,
            int passedCount,
            int totalCount
    ) {
        String topicKorean = mapTopicToKorean(problemTopic);
        String complexityInfo = expectedTimeComplexity != null
                ? expectedTimeComplexity
                : "LLM이 문제 분석 후 추론";

        return String.format("""
            당신은 알고리즘 교육 전문가이자 시니어 코드 리뷰어입니다.
            모든 피드백은 반드시 한국어로 작성합니다.

            ## 문제 컨텍스트
            - 프로그래밍 언어: %s
            - 출제 의도 (토픽): %s
            - 기대 최적 시간복잡도: %s
            - Judge0 채점 결과: %s
            - 테스트 통과율: %d/%d

            ## 평가 기준 (가중치)

            ### 1. 접근법 적합성 (35%%)
            사용자가 어떤 알고리즘/자료구조를 사용했는지 분석하고, 출제 의도(토픽)와 비교합니다.

            판정 기준:
            - MATCHED (100점): 출제 의도와 일치하는 접근법 사용
            - CREATIVE_BETTER (105점): 출제 의도와 다르지만 더 효율적인 창의적 풀이 ★
            - ALTERNATIVE_EQUAL (70점): 출제 의도와 다르고 효율성 동등
            - SUBOPTIMAL (50점): 출제 의도와 다르고 덜 효율적
            - BRUTEFORCE (30점): 최적화 없는 단순 완전탐색

            ### 2. 효율성 (35%%)
            코드의 시간/공간 복잡도를 분석하고, 기대 복잡도와 비교합니다.

            점수 계산 (기대 복잡도 대비 상대 평가):
            - 기대보다 효율적: 105점 ★
            - 기대와 동일: 100점
            - 1단계 비효율: 80점
            - 2단계 비효율: 60점
            - 3단계 이상 비효율: 40점 이하

            복잡도 순서: O(1) < O(log n) < O(n) < O(n log n) < O(n²) < O(n³) < O(2ⁿ)

            ### 3. 코드 품질 (30%%)
            가독성, 명명 규칙, 코드 구조를 평가합니다.

            평가 항목:
            - 가독성 (40%%): 들여쓰기, 빈 줄, 코드 블록 구분
            - 명명 규칙 (30%%): 변수/함수명의 명확성
            - 구조화 (30%%): 함수 분리, 중복 코드 제거

            ## 출력 형식 (반드시 JSON만 출력)
            ```json
            {
              "aiScore": 85,
              "feedback": "종합 피드백 (2-3문장, 한국어)",
              "codeQuality": "GOOD",
              "efficiency": "OPTIMAL",
              "readability": "GOOD",
              "approachVerdict": "MATCHED",
              "detectedApproach": "다이나믹 프로그래밍 (메모이제이션)",
              "detectedTimeComplexity": "O(n)",
              "detectedSpaceComplexity": "O(n)",
              "complexityExplanation": "복잡도 분석 설명 (한국어)",
              "strongPoints": ["잘한 점 1", "잘한 점 2"],
              "improvementTips": ["개선점 1", "개선점 2"],
              "nextStepSuggestion": "다음 학습 제안 (한국어)"
            }
            ```

            ## 주의사항
            - 모든 텍스트는 한국어로 작성
            - JSON 외의 텍스트는 출력하지 마세요
            - 코드 용어(Big-O, 알고리즘명 등)는 영어 유지 가능
            - 칭찬과 개선점을 균형있게 제시
            - 구체적이고 실행 가능한 조언 제공
            """, language, topicKorean, complexityInfo, judgeResult, passedCount, totalCount);
    }

    /**
     * 고도화된 사용자 프롬프트 생성
     */
    private String createEnhancedUserPrompt(String sourceCode, String problemDescription) {
        return String.format("""
            ## 문제 설명
            %s

            ## 사용자 제출 코드
            ```
            %s
            ```

            위 코드를 평가 기준에 따라 분석하고 JSON 형식으로 결과를 출력해주세요.
            """, problemDescription, sourceCode);
    }

    /**
     * 토픽 영문 → 한글 매핑
     */
    private String mapTopicToKorean(String topic) {
        if (topic == null) return "알고리즘";

        return switch (topic.toLowerCase()) {
            case "dp", "dynamic_programming" -> "다이나믹 프로그래밍 (DP)";
            case "greedy" -> "그리디";
            case "bfs" -> "너비 우선 탐색 (BFS)";
            case "dfs" -> "깊이 우선 탐색 (DFS)";
            case "binary_search" -> "이분 탐색";
            case "sorting" -> "정렬";
            case "two_pointer" -> "투 포인터";
            case "sliding_window" -> "슬라이딩 윈도우";
            case "graph", "graphs" -> "그래프";
            case "tree", "trees" -> "트리";
            case "stack" -> "스택";
            case "queue" -> "큐";
            case "heap", "priority_queue" -> "힙/우선순위 큐";
            case "hash", "hashing" -> "해시";
            case "string" -> "문자열";
            case "math" -> "수학";
            case "implementation" -> "구현";
            case "bruteforce", "bruteforcing" -> "완전 탐색";
            case "backtracking" -> "백트래킹";
            case "divide_and_conquer" -> "분할 정복";
            case "shortest_path" -> "최단 경로";
            case "disjoint_set", "union_find" -> "유니온 파인드";
            case "bitmask" -> "비트마스킹";
            default -> topic;
        };
    }

    /**
     * 고도화된 AI 응답 파싱
     */
    private AICodeEvaluationResult parseEnhancedAIResponse(String json) {
        // Markdown 코드블록 제거
        json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

        // 기본 필드 추출
        double aiScore = extractJsonDouble(json, "aiScore", 70);
        String feedback = extractJsonString(json, "feedback", "피드백을 생성하지 못했습니다.");
        String codeQuality = extractJsonString(json, "codeQuality", "FAIR");
        String readability = extractJsonString(json, "readability", "FAIR");

        // 새 필드 추출
        String efficiency = extractJsonString(json, "efficiency", "FAIR");
        String approachVerdict = extractJsonString(json, "approachVerdict", "UNKNOWN");
        String detectedApproach = extractJsonString(json, "detectedApproach", "");
        String detectedTimeComplexity = extractJsonString(json, "detectedTimeComplexity", "");
        String detectedSpaceComplexity = extractJsonString(json, "detectedSpaceComplexity", "");
        String complexityExplanation = extractJsonString(json, "complexityExplanation", "");
        String nextStepSuggestion = extractJsonString(json, "nextStepSuggestion", "");

        // 배열 필드 추출
        List<String> strongPoints = extractJsonArray(json, "strongPoints");
        List<String> improvementTips = extractJsonArray(json, "improvementTips");

        // 확장된 feedback 구성 (접근법 + 복잡도 + 기존 피드백)
        String enhancedFeedback = buildEnhancedFeedback(
                feedback, approachVerdict, detectedApproach,
                detectedTimeComplexity, detectedSpaceComplexity,
                complexityExplanation, strongPoints, improvementTips, nextStepSuggestion
        );

        return AICodeEvaluationResult.builder()
                .aiScore(aiScore)
                .feedback(enhancedFeedback)
                .codeQuality(codeQuality)
                .efficiency(efficiency)
                .readability(readability)
                .improvementTips(improvementTips.isEmpty() ? List.of("추가 개선점 없음") : improvementTips)
                .build();
    }

    /**
     * 확장된 피드백 문자열 구성
     */
    private String buildEnhancedFeedback(
            String baseFeedback,
            String approachVerdict,
            String detectedApproach,
            String timeComplexity,
            String spaceComplexity,
            String complexityExplanation,
            List<String> strongPoints,
            List<String> improvementTips,
            String nextStep
    ) {
        StringBuilder sb = new StringBuilder();

        // 종합 평가
        sb.append("## 📊 종합 평가\n");
        sb.append(baseFeedback).append("\n\n");

        // 접근법 분석
        if (detectedApproach != null && !detectedApproach.isEmpty()) {
            sb.append("## 🎯 접근법 분석\n");
            sb.append("- **사용한 접근법**: ").append(detectedApproach).append("\n");
            sb.append("- **판정**: ").append(mapVerdictToKorean(approachVerdict)).append("\n\n");
        }

        // 복잡도 분석
        if (timeComplexity != null && !timeComplexity.isEmpty()) {
            sb.append("## ⏱️ 복잡도 분석\n");
            sb.append("- **시간 복잡도**: ").append(timeComplexity).append("\n");
            if (spaceComplexity != null && !spaceComplexity.isEmpty()) {
                sb.append("- **공간 복잡도**: ").append(spaceComplexity).append("\n");
            }
            if (complexityExplanation != null && !complexityExplanation.isEmpty()) {
                sb.append("- **설명**: ").append(complexityExplanation).append("\n");
            }
            sb.append("\n");
        }

        // 잘한 점
        if (strongPoints != null && !strongPoints.isEmpty()) {
            sb.append("## ✅ 잘한 점\n");
            for (String point : strongPoints) {
                sb.append("- ").append(point).append("\n");
            }
            sb.append("\n");
        }

        // 개선점
        if (improvementTips != null && !improvementTips.isEmpty()) {
            sb.append("## 💡 개선 제안\n");
            for (String tip : improvementTips) {
                sb.append("- ").append(tip).append("\n");
            }
            sb.append("\n");
        }

        // 다음 학습 제안
        if (nextStep != null && !nextStep.isEmpty()) {
            sb.append("## 📚 다음 단계\n");
            sb.append(nextStep).append("\n");
        }

        return sb.toString();
    }

    /**
     * 판정 코드 → 한글 매핑
     */
    private String mapVerdictToKorean(String verdict) {
        if (verdict == null) return "평가 불가";

        return switch (verdict.toUpperCase()) {
            case "MATCHED" -> "✓ 출제 의도와 일치하는 접근법";
            case "CREATIVE_BETTER" -> "★ 창의적인 최적화! 출제 의도보다 효율적";
            case "ALTERNATIVE_EQUAL" -> "○ 대안적 접근법 (효율성 동등)";
            case "SUBOPTIMAL" -> "△ 비효율적 접근법 - 개선 필요";
            case "BRUTEFORCE" -> "▽ 단순 완전탐색 - 최적화 학습 권장";
            default -> verdict;
        };
    }

    /**
     * JSON 배열 추출
     */
    private List<String> extractJsonArray(String json, String key) {
        List<String> result = new ArrayList<>();
        try {
            // "key": ["item1", "item2", ...] 패턴 매칭
            var pattern = java.util.regex.Pattern.compile(
                    "\"" + key + "\"\\s*:\\s*\\[([^\\]]*)]"
            );
            var matcher = pattern.matcher(json);
            if (matcher.find()) {
                String arrayContent = matcher.group(1);
                // 각 문자열 아이템 추출
                var itemPattern = java.util.regex.Pattern.compile("\"([^\"]+)\"");
                var itemMatcher = itemPattern.matcher(arrayContent);
                while (itemMatcher.find()) {
                    result.add(itemMatcher.group(1));
                }
            }
        } catch (Exception e) {
            log.warn("JSON 배열 파싱 실패: key={}", key);
        }
        return result;
    }

    /**
     * 실패 시 기본 결과 생성
     */
    private AICodeEvaluationResult createFallbackResult(String errorMessage) {
        return AICodeEvaluationResult.builder()
                .aiScore(50.0)
                .feedback("AI 평가 도중 오류가 발생했습니다: " + errorMessage)
                .codeQuality("FAIR")
                .efficiency("UNKNOWN")
                .readability("UNKNOWN")
                .improvementTips(List.of("코드를 더 명확하게 작성해보세요."))
                .build();
    }

    // === JSON 파싱 유틸리티 메서드 ===

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
