package kr.or.kosa.backend.algorithm.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 알고리즘 동의어 사전
 * 한국어/영어 알고리즘 용어 매핑 및 확장
 * RAG 검색 시 동의어 확장에 사용
 */
@Slf4j
@Component
public class AlgorithmSynonymDictionary {

    /**
     * 알고리즘 동의어 그룹
     * key: 정규화된 알고리즘 ID
     * value: 해당 알고리즘의 모든 동의어 (한국어, 영어, 약어 등)
     */
    private static final Map<String, Set<String>> SYNONYM_GROUPS = Map.ofEntries(
            // === 기초 ===
            Map.entry("array", Set.of(
                    "배열", "array", "리스트", "list", "1차원 배열", "2차원 배열",
                    "행렬", "matrix", "벡터", "vector")),

            Map.entry("recursion", Set.of(
                    "재귀", "recursion", "recursive", "재귀 함수", "recursive function",
                    "재귀 호출", "재귀적")),

            Map.entry("simulation", Set.of(
                    "시뮬레이션", "simulation", "구현", "완전 탐색", "brute force",
                    "브루트포스", "완전탐색")),

            // === 탐색 알고리즘 ===
            Map.entry("binary_search", Set.of(
                    "이분 탐색", "이진 탐색", "binary search", "이분탐색", "이진탐색",
                    "parametric search", "파라메트릭 서치", "결정 문제")),

            Map.entry("bfs", Set.of(
                    "BFS", "bfs", "너비 우선 탐색", "breadth first search", "너비우선탐색",
                    "너비 우선", "breadth-first")),

            Map.entry("dfs", Set.of(
                    "DFS", "dfs", "깊이 우선 탐색", "depth first search", "깊이우선탐색",
                    "깊이 우선", "depth-first", "백트래킹", "backtracking")),

            // === 동적 프로그래밍 ===
            Map.entry("dp", Set.of(
                    "DP", "dp", "동적 프로그래밍", "dynamic programming", "다이나믹 프로그래밍",
                    "동적프로그래밍", "다이나믹프로그래밍", "메모이제이션", "memoization")),

            Map.entry("knapsack", Set.of(
                    "배낭 문제", "knapsack", "냅색", "배낭문제", "0/1 knapsack",
                    "fractional knapsack", "배낭 DP")),

            Map.entry("lis", Set.of(
                    "LIS", "lis", "최장 증가 부분 수열", "longest increasing subsequence",
                    "최장증가부분수열", "가장 긴 증가하는 부분 수열")),

            // === 그래프 알고리즘 ===
            Map.entry("graph", Set.of(
                    "그래프", "graph", "그래프 이론", "graph theory", "인접 리스트",
                    "인접 행렬", "adjacency list", "adjacency matrix", "정점", "간선",
                    "vertex", "edge", "노드", "node")),

            Map.entry("dijkstra", Set.of(
                    "다익스트라", "dijkstra", "최단 경로", "shortest path", "다익스트라 알고리즘",
                    "single source shortest path", "SSSP")),

            Map.entry("floyd_warshall", Set.of(
                    "플로이드 워셜", "floyd warshall", "플로이드", "floyd",
                    "모든 쌍 최단 경로", "all pairs shortest path", "APSP")),

            Map.entry("bellman_ford", Set.of(
                    "벨만 포드", "bellman ford", "벨만포드", "음수 가중치 최단 경로")),

            Map.entry("mst", Set.of(
                    "MST", "mst", "최소 신장 트리", "minimum spanning tree", "최소스패닝트리",
                    "크루스칼", "kruskal", "프림", "prim", "spanning tree")),

            Map.entry("topology_sort", Set.of(
                    "위상 정렬", "topological sort", "위상정렬", "topology sort",
                    "DAG", "directed acyclic graph")),

            // === 정렬 알고리즘 ===
            Map.entry("sorting", Set.of(
                    "정렬", "sorting", "sort", "퀵 정렬", "quicksort", "병합 정렬",
                    "merge sort", "힙 정렬", "heap sort")),

            // === 투 포인터 / 슬라이딩 윈도우 ===
            Map.entry("two_pointer", Set.of(
                    "투 포인터", "two pointer", "투포인터", "two pointers",
                    "슬라이딩 윈도우", "sliding window", "슬라이딩윈도우")),

            // === 그리디 ===
            Map.entry("greedy", Set.of(
                    "그리디", "greedy", "탐욕법", "탐욕 알고리즘", "greedy algorithm")),

            // === 분할 정복 ===
            Map.entry("divide_conquer", Set.of(
                    "분할 정복", "divide and conquer", "분할정복", "divide conquer")),

            // === 문자열 ===
            Map.entry("string", Set.of(
                    "문자열", "string", "문자열 처리", "string manipulation")),

            Map.entry("kmp", Set.of(
                    "KMP", "kmp", "문자열 매칭", "string matching", "패턴 매칭",
                    "knuth morris pratt")),

            // === 자료구조 ===
            Map.entry("stack", Set.of(
                    "스택", "stack", "LIFO")),

            Map.entry("queue", Set.of(
                    "큐", "queue", "FIFO", "덱", "deque", "양방향 큐")),

            Map.entry("stack_queue", Set.of(
                    "스택", "stack", "큐", "queue", "스택/큐", "LIFO", "FIFO",
                    "덱", "deque", "양방향 큐", "자료구조 기초")),

            Map.entry("priority_queue", Set.of(
                    "우선순위 큐", "priority queue", "힙", "heap", "최소 힙", "최대 힙",
                    "min heap", "max heap")),

            Map.entry("tree", Set.of(
                    "트리", "tree", "이진 트리", "binary tree", "이진트리")),

            Map.entry("segment_tree", Set.of(
                    "세그먼트 트리", "segment tree", "세그트리", "구간 트리", "인덱스 트리",
                    "fenwick tree", "펜윅 트리", "BIT")),

            Map.entry("union_find", Set.of(
                    "유니온 파인드", "union find", "분리 집합", "disjoint set", "DSU",
                    "union-find", "서로소 집합")),

            // === 수학 ===
            Map.entry("math", Set.of(
                    "수학", "math", "mathematics", "정수론", "number theory")),

            Map.entry("gcd_lcm", Set.of(
                    "최대공약수", "GCD", "gcd", "최소공배수", "LCM", "lcm",
                    "유클리드 호제법", "euclidean algorithm")),

            Map.entry("prime", Set.of(
                    "소수", "prime", "에라토스테네스", "sieve", "소수 판별",
                    "sieve of eratosthenes")),

            Map.entry("combination", Set.of(
                    "조합", "combination", "순열", "permutation", "조합론",
                    "combinatorics", "nCr", "nPr")),

            // === 기타 ===
            Map.entry("implementation", Set.of(
                    "구현", "implementation", "구현 문제", "코딩", "coding")),

            Map.entry("bit_manipulation", Set.of(
                    "비트마스킹", "bitmask", "bit manipulation", "비트 연산", "bitwise"))
    );

    /**
     * 쿼리를 동의어로 확장
     * 입력된 알고리즘 용어와 관련된 모든 동의어 반환
     *
     * @param query 검색 쿼리 (알고리즘 이름)
     * @return 확장된 동의어 집합
     */
    public Set<String> expand(String query) {
        Set<String> result = new HashSet<>();
        String lowerQuery = query.toLowerCase().trim();

        for (Set<String> group : SYNONYM_GROUPS.values()) {
            for (String synonym : group) {
                if (lowerQuery.contains(synonym.toLowerCase()) ||
                        synonym.toLowerCase().contains(lowerQuery)) {
                    result.addAll(group);
                    break;
                }
            }
        }

        // 매칭된 동의어가 없으면 원본 쿼리 반환
        if (result.isEmpty()) {
            result.add(query);
            log.debug("동의어 매칭 없음: '{}' → 원본 사용", query);
        } else {
            log.debug("동의어 확장: '{}' → {} 개 용어", query, result.size());
        }

        return result;
    }

    /**
     * 태그를 정규화된 알고리즘 ID로 변환
     *
     * @param tag 입력 태그 (한국어/영어)
     * @return 정규화된 알고리즘 ID (매칭 없으면 원본 반환)
     */
    public String normalize(String tag) {
        String lowerTag = tag.toLowerCase().trim();

        for (var entry : SYNONYM_GROUPS.entrySet()) {
            if (entry.getValue().stream()
                    .anyMatch(s -> s.toLowerCase().equals(lowerTag))) {
                return entry.getKey();
            }
        }

        return tag;
    }

    /**
     * RAG 검색용 쿼리 문자열 생성
     * 동의어를 공백으로 연결하여 검색 쿼리 생성
     *
     * @param algorithm  알고리즘 유형
     * @param difficulty 난이도
     * @return 검색 쿼리 문자열
     */
    public String buildSearchQuery(String algorithm, String difficulty) {
        Set<String> expandedTerms = expand(algorithm);

        // 대표 용어 3개 선택 (한국어 1개, 영어 2개 우선)
        StringBuilder query = new StringBuilder();
        int koreanCount = 0;
        int englishCount = 0;

        for (String term : expandedTerms) {
            boolean isKorean = term.matches(".*[가-힣]+.*");

            if (isKorean && koreanCount < 1) {
                query.append(term).append(" ");
                koreanCount++;
            } else if (!isKorean && englishCount < 2) {
                query.append(term).append(" ");
                englishCount++;
            }

            if (koreanCount >= 1 && englishCount >= 2) {
                break;
            }
        }

        query.append(difficulty).append(" algorithm problem");

        return query.toString().trim();
    }
}
