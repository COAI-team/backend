package kr.or.kosa.backend.algorithm.controller;

import kr.or.kosa.backend.algorithm.service.ProblemCrawlerService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 문제 크롤링 관리자 API 컨트롤러
 * 백준(BOJ)과 LeetCode 문제를 외부 API에서 가져와 저장
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/crawler")
@RequiredArgsConstructor
public class ProblemCrawlerController {

    private final ProblemCrawlerService crawlerService;

    /**
     * 백준 문제 크롤링
     *
     * POST /api/admin/crawler/boj
     * Body: {
     *   "query": "*s",          // 검색 쿼리 (기본: "*s")
     *   "count": 50,            // 가져올 문제 수 (기본: 50)
     *   "useAiRewrite": true    // AI 재서술 사용 여부 (기본: true)
     * }
     */
    @PostMapping("/boj")
    public ResponseEntity<?> crawlBojProblems(@RequestBody BojCrawlRequest request) {
        log.info("📥 백준 문제 크롤링 요청: {}", request);

        try {
            String query = request.getQuery() != null ? request.getQuery() : "*s";
            int count = request.getCount() != null ? request.getCount() : 50;
            boolean useAi = request.getUseAiRewrite() != null ? request.getUseAiRewrite() : true;

            int savedCount = crawlerService.fetchBojProblems(query, count, useAi);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "백준 문제 크롤링 완료",
                    "savedCount", savedCount,
                    "query", query,
                    "useAiRewrite", useAi
            ));

        } catch (Exception e) {
            log.error("백준 크롤링 실패", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "크롤링 중 오류 발생: " + e.getMessage()
            ));
        }
    }

    /**
     * LeetCode 문제 크롤링
     *
     * POST /api/admin/crawler/leetcode
     * Body: {
     *   "count": 20,            // 가져올 문제 수 (기본: 20)
     *   "useAiRewrite": true,   // AI 재서술 사용 여부 (기본: true)
     *   "difficulty": "EASY"    // 난이도 필터 (EASY, MEDIUM, HARD, null=전체)
     * }
     */
    @PostMapping("/leetcode")
    public ResponseEntity<?> crawlLeetCodeProblems(@RequestBody LeetCodeCrawlRequest request) {
        log.info("📥 LeetCode 문제 크롤링 요청: {}", request);

        try {
            int count = request.getCount() != null ? request.getCount() : 20;
            boolean useAi = request.getUseAiRewrite() != null ? request.getUseAiRewrite() : true;
            String difficulty = request.getDifficulty();

            int savedCount = crawlerService.fetchLeetCodeProblems(count, useAi, difficulty);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "LeetCode 문제 크롤링 완료",
                    "savedCount", savedCount,
                    "difficulty", difficulty != null ? difficulty : "ALL",
                    "useAiRewrite", useAi
            ));

        } catch (Exception e) {
            log.error("LeetCode 크롤링 실패", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "크롤링 중 오류 발생: " + e.getMessage()
            ));
        }
    }

    /**
     * 크롤링 상태 조회
     *
     * GET /api/admin/crawler/status
     */
    @GetMapping("/status")
    public ResponseEntity<?> getCrawlerStatus() {
        try {
            String status = crawlerService.getCrawlerStatus();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "status", status
            ));
        } catch (Exception e) {
            log.error("상태 조회 실패", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "상태 조회 실패: " + e.getMessage()
            ));
        }
    }

    // ===== Request DTOs =====

    @Data
    public static class BojCrawlRequest {
        private String query;           // 검색 쿼리 (예: "*s", "tier:b")
        private Integer count;          // 가져올 문제 수
        private Boolean useAiRewrite;   // AI 재서술 사용 여부
    }

    @Data
    public static class LeetCodeCrawlRequest {
        private Integer count;          // 가져올 문제 수
        private Boolean useAiRewrite;   // AI 재서술 사용 여부
        private String difficulty;      // 난이도 (EASY, MEDIUM, HARD)
    }
}
