package kr.or.kosa.backend.algorithm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;

/**
 * AI를 사용한 문제 재서술 서비스
 * 저작권 이슈를 방지하기 위해 원본 문제를 AI로 재작성
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemRewriteService {

    private final OpenAiChatModel chatModel;

    /**
     * 문제 제목과 설명을 AI로 재서술
     *
     * @param originalTitle       원본 제목
     * @param originalDescription 원본 설명 (간단한 정보)
     * @param difficulty         난이도
     * @param tags               태그 목록
     * @return 재서술된 문제 설명
     */
    public String rewriteProblemDescription(
            String originalTitle,
            String originalDescription,
            String difficulty,
            String tags
    ) {
        log.info("🤖 AI 문제 재서술 시작: title={}", originalTitle);

        String prompt = buildPrompt(originalTitle, originalDescription, difficulty, tags);

        try {
            String rewritten = chatModel.call(prompt);
            log.info("✅ AI 재서술 완료");
            return rewritten;

        } catch (Exception e) {
            log.error("❌ AI 재서술 실패: {}", e.getMessage());
            return generateFallbackDescription(originalTitle, difficulty, tags);
        }
    }

    /**
     * AI 프롬프트 생성
     */
    private String buildPrompt(String title, String description, String difficulty, String tags) {
        return String.format("""
                당신은 코딩테스트 문제 생성 도우미입니다.

                다음 알고리즘 문제를 저작권 침해 없이 완전히 새롭게 재작성해주세요:

                **원본 정보:**
                - 제목: %s
                - 설명: %s
                - 난이도: %s
                - 태그: %s

                **요구사항:**
                1. 문제의 핵심 알고리즘 개념은 유지하되, 문제 상황과 스토리는 완전히 새롭게 작성
                2. 입력/출력 형식을 명확하게 기술
                3. 예제 입력과 출력 2개 이상 포함
                4. 제약사항 명시
                5. 마크다운 형식으로 작성

                **출력 형식:**
                ```markdown
                ## 문제 설명
                [새로운 문제 스토리]

                ## 입력
                [입력 형식]

                ## 출력
                [출력 형식]

                ## 제약사항
                - [제약사항 1]
                - [제약사항 2]

                ## 예제

                ### 예제 1
                **입력:**
                ```
                [예제 입력 1]
                ```

                **출력:**
                ```
                [예제 출력 1]
                ```

                ### 예제 2
                **입력:**
                ```
                [예제 입력 2]
                ```

                **출력:**
                ```
                [예제 출력 2]
                ```

                ## 힌트
                [문제 풀이 힌트]
                ```

                위 형식으로 완전히 새로운 문제를 작성해주세요.
                """, title, description, difficulty, tags);
    }

    /**
     * AI 호출 실패 시 대체 설명 생성
     */
    private String generateFallbackDescription(String title, String difficulty, String tags) {
        return String.format("""
                ## 문제: %s

                **난이도:** %s
                **태그:** %s

                > 이 문제는 외부 API에서 가져온 문제입니다.
                > AI 재서술이 진행 중이니 잠시 후 다시 확인해주세요.

                자세한 내용은 원본 문제를 참고하세요.
                """, title, difficulty, tags);
    }

    /**
     * 배치 처리용: 여러 문제를 순차적으로 재서술
     * (Rate limiting 방지를 위해 지연 시간 추가)
     */
    public String rewriteProblemDescriptionWithDelay(
            String originalTitle,
            String originalDescription,
            String difficulty,
            String tags
    ) {
        String result = rewriteProblemDescription(originalTitle, originalDescription, difficulty, tags);

        try {
            Thread.sleep(2000);  // 2초 대기 (OpenAI Rate Limit 방지)
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return result;
    }
}
