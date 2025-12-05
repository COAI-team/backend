package kr.or.kosa.backend.algorithm.dto.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProblemTopic {

    ARRAY("배열"),
    DP("다이나믹 프로그래밍"),
    GREEDY("그리디"),
    GRAPH("그래프"),
    IMPLEMENTATION("구현"),
    MATH("수학"),
    STRING("문자열"),
    SORTING("정렬"),
    SEARCH("탐색"),
    SIMULATION("시뮬레이션"),
    RECURSION("재귀"),
    BACKTRACKING("백트래킹"),
    BFS("너비우선탐색"),
    DFS("깊이우선탐색"),
    BINARY_SEARCH("이분탐색");

    private final String displayName;

    public static ProblemTopic fromDisplayName(String name) {
        for (ProblemTopic topic : values()) {
            if (topic.displayName.equals(name)) {
                return topic;
            }
        }
        throw new IllegalArgumentException("Unknown topic: " + name);
    }
}
