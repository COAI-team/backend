package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.dto.request.ProblemGenerationRequestDto;
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
                3. 난이도별 문제 특성 가이드라인:
                    **BRONZE** (알고리즘 입문자 대상):
                    - 알고리즘: 기본 구현, 브루트포스, 기본 정렬, 간단한 수학
                    - 입력 크기: N ≤ 1,000
                    - 특성: 단순 구현, 기초 문법 활용, 중학교 수준 수학 지식
                    - 시간복잡도: O(N²) 이하로 충분히 해결 가능
                    **SILVER** (코딩 테스트 중위 난이도):
                    - 알고리즘: 스택/큐, 이분 탐색, DFS/BFS 기초, 기본 DP, 그리디 입문
                    - 입력 크기: N ≤ 100,000
                    - 특성: 기본 자료구조 활용, 표준 알고리즘 적용
                    - 시간복잡도: O(N log N) ~ O(N²) 사이 최적해 요구
                    **GOLD** (코딩 테스트 상위 난이도):
                    - 알고리즘: 고급 DP, 다익스트라/플로이드, MST, 백트래킹, 투 포인터
                    - 입력 크기: N ≤ 500,000
                    - 특성: 복합 알고리즘 조합, 최적화 필요, 엣지 케이스 고려
                    - 시간복잡도: O(N log N) 이하 최적해 필수
                    **PLATINUM** (대회 중상위 난이도):
                    - 알고리즘: DP 최적화, 세그먼트 트리, 이분 매칭, 네트워크 유량, KMP
                    - 입력 크기: N ≤ 1,000,000
                    - 특성: 고급 자료구조 필수, 복잡한 알고리즘 조합, 수학적 사고
                    - 시간복잡도: O(N log N) 또는 O(N) 최적해 필수
                4. Optimal 코드는 시간 제한 내 통과해야 합니다.
                5. Naive 코드는 큰 입력에서 시간초과가 발생해야 합니다.

                ## 금지 사항
                - 상표권이 있는 캐릭터/브랜드명 사용 금지 (예: 마리오, 포켓몬, 디즈니 캐릭터 등)
                - 기존 유명 문제와 동일한 스토리 금지
                - 불명확하거나 애매한 제약 조건 금지

                ## 응답 형식
                반드시 유효한 JSON으로만 응답하세요. 다른 텍스트는 절대 포함하지 마세요.
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
                    {"input": "5\\n1 3 5 7 9", "output": "YES", "isSample": true, "description": "기본 케이스"},
                    {"input": "3\\n10 20 30", "output": "NO", "isSample": true, "description": "다른 케이스"},
                    {"input": "10\\n1 2 3 4 5 6 7 8 9 10", "output": "55", "isSample": false, "description": "경계값 테스트"},
                    {"input": "1\\n1000000000", "output": "1000000000", "isSample": false, "description": "코너 케이스"},
                    {"input": "100\\n1 2 3 ... 100", "output": "5050", "isSample": false, "description": "큰 입력 테스트"}
                  ],
                  "optimalCode": "Python 최적 풀이 코드 (전체 코드)",
                  "naiveCode": "Python 비효율적 풀이 코드 (시간 초과 발생해야 함)",
                  "tags": ["알고리즘 태그 1", "알고리즘 태그 2"]
                }
                ```

                **중요 (필수 사항):**
                - JSON만 출력하고 다른 설명은 절대 포함하지 마세요.
                - **optimalCode와 naiveCode는 반드시 포함해야 합니다! 이 두 필드는 필수입니다.**
                - optimalCode와 naiveCode는 완전한 Python 코드여야 합니다 (입력 받기부터 출력까지).
                - 코드는 반드시 실행 가능해야 합니다. 함수를 정의했다면 마지막에 반드시 호출하세요!
                - 올바른 예시: def solve(): ...코드... \\n\\nsolve()  (마지막에 solve() 호출)
                - 잘못된 예시: def solve(): ...코드...  (함수 정의만 하고 호출 안함)
                - testCases의 input/output은 실제 프로그램 입출력 형식과 일치해야 합니다.
                - expectedTimeComplexity도 반드시 포함하세요.

                **JSON 형식 필수 규칙:**
                - 모든 문자열 값은 하나의 연속된 문자열이어야 합니다.
                - 문자열 연결 연산자(+)를 절대 사용하지 마세요.
                - 코드의 줄바꿈은 반드시 \\n 이스케이프 시퀀스로 표현하세요.
                - 예시: "optimalCode": "def solve():\\n    n = int(input())\\n    print(n)"
                - 잘못된 예시: "optimalCode": "def solve():" + "\\n    n = int(input())"

                **테스트케이스 데이터 규칙:**
                - input과 output은 반드시 실제 문자열 데이터로 작성하세요.
                - Python 코드나 표현식 (join, range, for 등)을 절대 사용하지 마세요.
                - 잘못된 예시: "input": "".join(str(x) for x in range(100))
                - 올바른 예시: "input": "1 2 3 4 5"
                - 큰 데이터가 필요한 경우 적당한 크기(10~100개)의 실제 데이터를 작성하세요.
                - 실제 실행 가능한 구체적인 데이터를 사용하세요.
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
