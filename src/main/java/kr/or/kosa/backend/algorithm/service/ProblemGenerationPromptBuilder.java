package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.dto.ProblemGenerationRequestDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 알고리즘 문제 생성 프롬프트 빌더
 * RAG 기반 Few-shot 학습을 위한 프롬프트 구성
 */
@Slf4j
@Component
public class ProblemGenerationPromptBuilder {

    /**
     * 시스템 프롬프트 생성
     * 품질 기준 및 제약 조건 정의
     */
    public String buildSystemPrompt() {
        return """
                당신은 알고리즘 문제 출제 전문가입니다.

                ## 역할
                - 논리적으로 일관되고 풀이 가능한 알고리즘 문제 생성
                - 명확한 제약 조건과 입출력 형식 제공
                - 검증 가능한 테스트케이스 생성

                ## 품질 기준
                1. 문제 설명과 제약조건이 모순 없이 일치해야 합니다.
                2. 테스트케이스는 최소 5개, 경계값과 특수값을 반드시 포함해야 합니다.
                3. 난이도별 입력 크기 가이드라인:
                   - BRONZE: N ≤ 1,000
                   - SILVER: N ≤ 100,000
                   - GOLD: N ≤ 500,000
                   - PLATINUM: N ≤ 1,000,000
                4. Optimal 코드는 시간 제한 내 통과해야 합니다.
                5. Naive 코드는 큰 입력에서 시간초과가 발생해야 합니다.

                ## 금지 사항
                - 상표권이 있는 캐릭터/브랜드명 사용 금지 (예: 마리오, 포켓몬, 디즈니 캐릭터 등)
                - 기존 유명 문제와 동일한 스토리 금지
                - 불명확하거나 애매한 제약 조건 금지

                ## 응답 형식
                반드시 유효한 JSON으로만 응답하세요. 다른 텍스트는 포함하지 마세요.
                """;
    }

    /**
     * 사용자 프롬프트 생성 (RAG 예시 포함)
     *
     * @param request    문제 생성 요청
     * @param references RAG로 검색된 참조 문제 목록
     * @return 구성된 사용자 프롬프트
     */
    public String buildUserPrompt(ProblemGenerationRequestDto request, List<Document> references) {
        StringBuilder sb = new StringBuilder();

        // 1. Few-shot 예시 추가 (RAG 결과)
        if (references != null && !references.isEmpty()) {
            sb.append("## 참고 문제 예시\n\n");
            sb.append("아래 예시들의 서술 방식, 구조, 품질을 참고하여 새로운 문제를 생성하세요.\n\n");

            for (int i = 0; i < references.size(); i++) {
                Document ref = references.get(i);
                String title = getMetadata(ref, "title", "예시 " + (i + 1));
                String difficulty = getMetadata(ref, "difficulty", "N/A");
                String tags = getMetadata(ref, "tags", "N/A");

                sb.append(String.format("### 예시 %d: %s\n", i + 1, title));
                sb.append(String.format("난이도: %s | 태그: %s\n\n", difficulty, tags));
                sb.append(ref.getText());
                sb.append("\n\n---\n\n");
            }
        }

        // 2. 생성 요청 본문
        sb.append("## 새로운 문제 생성 요청\n\n");

        if (references != null && !references.isEmpty()) {
            sb.append("위 예시들의 서술 방식을 참고하여 **완전히 새로운** 문제를 생성하세요.\n\n");
        }

        sb.append(String.format("- 알고리즘: %s\n", request.getTopic()));
        sb.append(String.format("- 난이도: %s\n", request.getDifficulty()));

        if (request.getTimeLimit() != null) {
            sb.append(String.format("- 시간 제한: %d ms\n", request.getTimeLimit()));
        }

        if (request.getMemoryLimit() != null) {
            sb.append(String.format("- 메모리 제한: %d MB\n", request.getMemoryLimit()));
        }

        if (request.getAdditionalRequirements() != null && !request.getAdditionalRequirements().isBlank()) {
            sb.append(String.format("- 스토리 테마: %s\n", request.getAdditionalRequirements()));
        }

        // 3. JSON 응답 형식
        sb.append("""

                **응답 형식 (JSON):**
                ```json
                {
                  "title": "문제 제목",
                  "description": "문제 설명 (스토리텔링 포함, 입력/출력 설명 포함하지 않음)",
                  "constraints": "제약 조건 (예: 1 ≤ N ≤ 100,000)",
                  "inputFormat": "입력 형식 설명",
                  "outputFormat": "출력 형식 설명",
                  "timeLimit": 1000,
                  "memoryLimit": 256,
                  "expectedTimeComplexity": "O(n log n)",
                  "testCases": [
                    {"input": "예제 입력 1", "output": "예제 출력 1", "isSample": true, "description": "기본 케이스"},
                    {"input": "예제 입력 2", "output": "예제 출력 2", "isSample": true, "description": "다른 케이스"},
                    {"input": "큰 입력 데이터", "output": "예상 출력", "isSample": false, "description": "경계값 테스트"},
                    {"input": "특수 케이스", "output": "특수 출력", "isSample": false, "description": "코너 케이스"},
                    {"input": "최대 입력", "output": "최대 출력", "isSample": false, "description": "최대 크기 테스트"}
                  ],
                  "optimalCode": "Python 최적 풀이 코드 (전체 코드)",
                  "naiveCode": "Python 비효율적 풀이 코드 (시간 초과 발생해야 함)",
                  "tags": ["알고리즘 태그 1", "알고리즘 태그 2"],
                  "hint": "힌트 (선택사항, 없으면 null)"
                }
                ```

                **중요:**
                - JSON만 출력하고 다른 설명은 절대 포함하지 마세요.
                - optimalCode와 naiveCode는 완전한 Python 코드여야 합니다 (입력 받기부터 출력까지).
                - testCases의 input/output은 실제 프로그램 입출력 형식과 일치해야 합니다.
                """);

        return sb.toString();
    }

    /**
     * 지침만 사용하는 프롬프트 생성 (RAG 없음, 실험용)
     */
    public String buildUserPromptWithoutRag(ProblemGenerationRequestDto request) {
        return buildUserPrompt(request, null);
    }

    /**
     * RAG만 사용하는 프롬프트의 시스템 프롬프트 (실험용)
     */
    public String buildMinimalSystemPrompt() {
        return """
                알고리즘 문제를 생성하세요.
                응답은 JSON 형식으로 작성하세요.
                """;
    }

    /**
     * 문서 메타데이터 안전하게 가져오기
     */
    private String getMetadata(Document doc, String key, String defaultValue) {
        if (doc.getMetadata() == null) {
            return defaultValue;
        }
        Object value = doc.getMetadata().get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
