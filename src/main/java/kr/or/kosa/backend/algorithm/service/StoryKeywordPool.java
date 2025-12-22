package kr.or.kosa.backend.algorithm.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 스토리 키워드 풀
 *
 * 목적: 테마별로 다양한 스토리 소재를 제공하여 LLM이 매번 다른 문제를 생성하도록 유도
 *
 * 구현 논리:
 * 1. 각 테마별 20개의 구체적인 스토리 소재 정의
 * 2. 문제 생성 시 랜덤으로 3개를 선택하여 프롬프트에 포함
 * 3. LLM은 선택된 키워드를 문제 스토리에 녹여서 생성
 * 4. 같은 테마라도 키워드 조합이 달라 매번 다른 스토리 생성
 *
 * 키워드 선별 원칙:
 * - 알고리즘 힌트가 되는 표현 제외 (경로, 최적화, 탐색, 계산, 배치 등)
 * - 순수한 스토리 소재만 포함 (장소, 물건, 캐릭터, 행동, 상황, 감정 등)
 * - 구체적이고 상상하기 쉬운 소재
 * - 다양한 스토리로 확장 가능한 일반적인 소재
 *
 * 테마 목록 (ProblemPoolController.ACTIVE_THEMES와 동기화):
 * - SANTA_DELIVERY: 산타의 선물 배달
 * - SNOWBALL_FIGHT: 눈싸움 대작전
 * - CHRISTMAS_TREE: 크리스마스 트리 장식
 * - NEW_YEAR_FIREWORKS: 새해 불꽃놀이
 * - SKI_RESORT: 스키장
 */
@Slf4j
@Component
public class StoryKeywordPool {

    /**
     * 테마별 스토리 키워드 풀 (각 20개)
     *
     * 키워드는 순수한 스토리 소재로, 알고리즘 유형과 무관하게 설계됨
     * 이를 통해 같은 알고리즘 문제라도 다양한 스토리가 생성될 수 있음
     */
    private static final Map<String, List<String>> KEYWORD_POOL = Map.of(

            // 산타의 선물 배달 - 크리스마스 이브 분위기의 소재들
            "SANTA_DELIVERY", List.of(
                    "빨간 양말", "굴뚝 속 쿠키", "밤하늘의 별", "루돌프의 빨간 코",
                    "눈 덮인 지붕", "잠든 아이들", "반짝이는 포장지", "따뜻한 벽난로",
                    "크리스마스 캐럴", "하얀 눈송이", "산타의 큰 자루", "마법의 종소리",
                    "차가운 겨울바람", "소원 편지", "북극의 작업장", "요정들의 도움",
                    "썰매의 방울소리", "초콜릿 쿠키", "우유 한 잔", "새벽녘의 고요함"
            ),

            // 눈싸움 대작전 - 겨울 마을 눈싸움의 소재들
            "SNOWBALL_FIGHT", List.of(
                    "하얀 눈밭", "빨간 장갑", "언덕 위 진지", "숨어있는 친구",
                    "날아가는 눈덩이", "얼어붙은 연못", "눈사람 방패", "겨울 방학",
                    "따뜻한 핫초코", "젖은 목도리", "신나는 함성", "눈송이 세례",
                    "미끄러운 언덕", "두꺼운 패딩", "얼음 요새", "털모자",
                    "빨개진 볼", "하얀 입김", "눈 쌓인 나무", "승리의 환호"
            ),

            // 크리스마스 트리 장식 - 트리 꾸미기의 소재들
            "CHRISTMAS_TREE", List.of(
                    "반짝이는 전구", "금색 오너먼트", "꼭대기 별", "빨간 리본",
                    "초록 가지", "은색 틴셀", "작은 종", "사탕 지팡이",
                    "천사 장식", "눈꽃 모양", "진저브레드 맨", "미니어처 선물 상자",
                    "루돌프 장식", "색색의 전구", "빈티지 오너먼트", "가족 사진 장식",
                    "수제 리스", "솔방울", "금빛 별 가랜드", "아늑한 거실"
            ),

            // 새해 불꽃놀이 - 새해 전야 축제의 소재들
            "NEW_YEAR_FIREWORKS", List.of(
                    "자정의 카운트다운", "밤하늘의 불꽃", "새해 소원", "함께하는 가족",
                    "떡국 한 그릇", "세뱃돈 봉투", "복주머니", "새해 첫 해돋이",
                    "타종 행사", "환호하는 군중", "색색의 폭죽", "반짝이는 샴페인",
                    "새해 인사", "덕담", "첫눈", "소망 등",
                    "새해 결심", "12시 종소리", "축제 음악", "행복한 미소"
            ),

            // 스키장 - 겨울 스키 리조트의 소재들
            "SKI_RESORT", List.of(
                    "하얀 슬로프", "스키 고글", "리프트 줄", "따뜻한 롯지",
                    "눈 덮인 산", "스키 폴", "보드 타는 친구", "설원의 일출",
                    "고급 샬레", "뜨거운 코코아", "스키 부츠", "설경 사진",
                    "곤돌라", "눈꽃 나무", "스키 강습", "초보자의 첫 도전",
                    "급경사 코스", "눈보라", "아프레 스키", "별빛 아래 야간 스키"
            )
    );

    /**
     * 지정된 테마에서 랜덤으로 키워드를 선택
     *
     * @param theme 테마 키 (예: "SANTA_DELIVERY")
     * @param count 선택할 키워드 개수 (기본 권장: 3개)
     * @return 선택된 키워드 목록 (테마가 없으면 빈 목록)
     */
    public List<String> getRandomKeywords(String theme, int count) {
        List<String> pool = new ArrayList<>(KEYWORD_POOL.getOrDefault(theme, List.of()));

        if (pool.isEmpty()) {
            log.debug("테마 '{}' 에 대한 키워드 풀이 없습니다.", theme);
            return List.of();
        }

        Collections.shuffle(pool);
        List<String> selected = pool.subList(0, Math.min(count, pool.size()));

        log.debug("테마 '{}' 에서 키워드 {}개 선택: {}", theme, selected.size(), selected);
        return new ArrayList<>(selected); // subList는 원본 리스트에 의존하므로 새 리스트로 반환
    }

    /**
     * 테마 존재 여부 확인
     *
     * @param theme 테마 키
     * @return 테마가 등록되어 있으면 true
     */
    public boolean hasTheme(String theme) {
        return KEYWORD_POOL.containsKey(theme);
    }

    /**
     * 등록된 모든 테마 키 조회
     *
     * @return 테마 키 집합
     */
    public Set<String> getAvailableThemes() {
        return KEYWORD_POOL.keySet();
    }

    /**
     * 특정 테마의 전체 키워드 목록 조회
     *
     * @param theme 테마 키
     * @return 키워드 목록 (없으면 빈 목록)
     */
    public List<String> getAllKeywords(String theme) {
        return KEYWORD_POOL.getOrDefault(theme, List.of());
    }
}
