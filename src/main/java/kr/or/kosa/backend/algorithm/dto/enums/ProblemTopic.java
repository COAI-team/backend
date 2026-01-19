package kr.or.kosa.backend.algorithm.dto.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 알고리즘 문제 토픽 (프론트엔드 TOPIC_CATEGORIES_ALGO와 동기화)
 */
@Getter
@RequiredArgsConstructor
public enum ProblemTopic {

    // ===== 자료구조 =====
    HASH("해시", "자료구조"),
    STACK_QUEUE("스택/큐", "자료구조"),
    HEAP("힙/우선순위 큐", "자료구조"),
    TREE("트리", "자료구조"),

    // ===== 탐색 =====
    DFS_BFS("DFS/BFS", "탐색"),
    BRUTE_FORCE("완전탐색", "탐색"),
    BACKTRACKING("백트래킹", "탐색"),
    BINARY_SEARCH("이분탐색", "탐색"),
    GRAPH_SHORTEST_PATH("그래프/최단경로", "탐색"),

    // ===== 최적화 =====
    GREEDY("그리디", "최적화"),
    DP("동적 프로그래밍(DP)", "최적화"),

    // ===== 구현 =====
    IMPLEMENTATION("구현/시뮬레이션", "구현"),
    SORTING("정렬", "구현"),
    STRING("문자열 처리", "구현"),
    TWO_POINTER("투포인터/슬라이딩 윈도우", "구현");

    private final String displayName;
    private final String category;

    /**
     * displayName으로 ProblemTopic 찾기
     */
    public static ProblemTopic fromDisplayName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        for (ProblemTopic topic : values()) {
            if (topic.displayName.equals(name)) {
                return topic;
            }
        }
        return null;
    }

    /**
     * enum name 또는 displayName으로 찾기
     */
    public static ProblemTopic fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        for (ProblemTopic topic : values()) {
            if (topic.name().equalsIgnoreCase(value) ||
                topic.displayName.equals(value)) {
                return topic;
            }
        }
        return null;
    }
}
