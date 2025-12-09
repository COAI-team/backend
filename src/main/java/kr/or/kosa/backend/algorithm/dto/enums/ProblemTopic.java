package kr.or.kosa.backend.algorithm.dto.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProblemTopic {

    // ===== 기초 (6개) =====
    ARRAY("배열", "array"),
    IMPLEMENTATION("구현", "implementation"),
    SIMULATION("시뮬레이션", "implementation"),
    RECURSION("재귀", "implementation"),
    MATH("수학", "math"),
    STRING("문자열", "string"),

    // ===== 탐색 (5개) =====
    SEARCH("탐색", "binary_search"),
    BFS("너비우선탐색", "bfs"),
    DFS("깊이우선탐색", "dfs"),
    BINARY_SEARCH("이분탐색", "binary_search"),
    BACKTRACKING("백트래킹", "dfs"),

    // ===== 알고리즘 (5개) =====
    DP("다이나믹 프로그래밍", "dp"),
    GREEDY("그리디", "greedy"),
    SORTING("정렬", "sorting"),
    DIVIDE_CONQUER("분할정복", "divide_conquer"),
    TWO_POINTER("투포인터", "two_pointer"),

    // ===== 그래프 (4개) =====
    GRAPH("그래프", "graph"),
    SHORTEST_PATH("최단경로", "dijkstra"),
    MST("최소신장트리", "mst"),
    TOPOLOGY_SORT("위상정렬", "topology_sort"),

    // ===== 자료구조 (4개) =====
    STACK_QUEUE("스택/큐", "stack_queue"),
    TREE("트리", "tree"),
    HEAP("힙", "priority_queue"),
    UNION_FIND("유니온파인드", "union_find");

    private final String displayName;
    private final String synonymKey;

    // 프론트엔드 약어 → Enum 매핑
    private static final java.util.Map<String, ProblemTopic> ALIAS_MAP = java.util.Map.of(
            "BFS", BFS,
            "DFS", DFS,
            "DP", DP,
            "MST", MST
    );

    public static ProblemTopic fromDisplayName(String name) {
        // 약어 먼저 확인
        if (ALIAS_MAP.containsKey(name)) {
            return ALIAS_MAP.get(name);
        }

        // displayName으로 검색
        for (ProblemTopic topic : values()) {
            if (topic.displayName.equals(name)) {
                return topic;
            }
        }
        throw new IllegalArgumentException("Unknown topic: " + name);
    }

    public static ProblemTopic fromValue(String value) {
        for (ProblemTopic topic : values()) {
            if (topic.name().equalsIgnoreCase(value) ||
                topic.displayName.equals(value)) {
                return topic;
            }
        }
        throw new IllegalArgumentException("Unknown topic value: " + value);
    }
}
