package kr.or.kosa.backend.algorithm.controller;

import kr.or.kosa.backend.algorithm.dto.request.BojCrawlRequest;
import kr.or.kosa.backend.algorithm.dto.request.LeetCodeCrawlRequest;
import kr.or.kosa.backend.algorithm.dto.request.VectorDbCrawlRequest;
import kr.or.kosa.backend.algorithm.service.ProblemCrawlerService;
import kr.or.kosa.backend.algorithm.service.ProblemVectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 문제 크롤링 관리자 API 컨트롤러
 * 백준(BOJ)과 LeetCode 문제를 외부 API에서 가져와 저장
 */
@Slf4j
@RestController
@RequestMapping("/algo/crawler")
@RequiredArgsConstructor
public class ProblemCrawlerController {

    private final ProblemCrawlerService crawlerService;
    private final ProblemVectorStoreService vectorStoreService;

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

    // ===== Vector DB 전용 API =====

    /**
     * BOJ 문제를 Vector DB에만 수집 (RAG용)
     *
     * POST /api/admin/crawler/vectordb/boj
     * Body: {
     *   "query": "*s",     // 검색 쿼리
     *   "count": 100       // 수집할 문제 수
     * }
     */
    @PostMapping("/vectordb/boj")
    public ResponseEntity<?> collectBojToVectorDb(@RequestBody VectorDbCrawlRequest request) {
        log.info("📥 BOJ → Vector DB 수집 요청: {}", request);

        try {
            String query = request.getQuery() != null ? request.getQuery() : "*s";
            int count = request.getCount() != null ? request.getCount() : 100;

            int savedCount = crawlerService.collectBojToVectorDb(query, count);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "BOJ 문제 Vector DB 수집 완료",
                    "savedCount", savedCount,
                    "query", query,
                    "target", "VectorDB"
            ));

        } catch (Exception e) {
            log.error("Vector DB 수집 실패", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "수집 중 오류 발생: " + e.getMessage()
            ));
        }
    }

    /**
     * BOJ 문제를 MySQL + Vector DB 모두에 수집
     *
     * POST /api/admin/crawler/boj/full
     * Body: {
     *   "query": "*s",
     *   "count": 50,
     *   "useAiRewrite": false
     * }
     */
    @PostMapping("/boj/full")
    public ResponseEntity<?> crawlBojWithVectorDb(@RequestBody BojCrawlRequest request) {
        log.info("📥 BOJ → MySQL + Vector DB 수집 요청: {}", request);

        try {
            String query = request.getQuery() != null ? request.getQuery() : "*s";
            int count = request.getCount() != null ? request.getCount() : 50;
            boolean useAi = request.getUseAiRewrite() != null ? request.getUseAiRewrite() : false;

            int savedCount = crawlerService.fetchBojProblemsWithVectorDb(query, count, useAi);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "BOJ 문제 MySQL + Vector DB 수집 완료",
                    "savedCount", savedCount,
                    "query", query,
                    "useAiRewrite", useAi,
                    "target", "MySQL + VectorDB"
            ));

        } catch (Exception e) {
            log.error("전체 수집 실패", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "수집 중 오류 발생: " + e.getMessage()
            ));
        }
    }

    /**
     * LeetCode 문제를 Vector DB에만 수집 (RAG용)
     *
     * POST /api/admin/crawler/vectordb/leetcode
     * Body: {
     *   "count": 50,           // 수집할 문제 수
     *   "difficulty": "MEDIUM" // 난이도 필터 (선택)
     * }
     */
    @PostMapping("/vectordb/leetcode")
    public ResponseEntity<?> collectLeetCodeToVectorDb(@RequestBody LeetCodeCrawlRequest request) {
        log.info("📥 LeetCode → Vector DB 수집 요청: {}", request);

        try {
            int count = request.getCount() != null ? request.getCount() : 50;
            String difficulty = request.getDifficulty();

            int savedCount = crawlerService.collectLeetCodeToVectorDb(count, difficulty);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "LeetCode 문제 Vector DB 수집 완료",
                    "savedCount", savedCount,
                    "difficulty", difficulty != null ? difficulty : "ALL",
                    "target", "VectorDB"
            ));

        } catch (Exception e) {
            log.error("Vector DB 수집 실패", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "수집 중 오류 발생: " + e.getMessage()
            ));
        }
    }

    /**
     * LeetCode 문제를 MySQL + Vector DB 모두에 수집
     *
     * POST /api/admin/crawler/leetcode/full
     * Body: {
     *   "count": 20,
     *   "useAiRewrite": false,
     *   "difficulty": "EASY"
     * }
     */
    @PostMapping("/leetcode/full")
    public ResponseEntity<?> crawlLeetCodeWithVectorDb(@RequestBody LeetCodeCrawlRequest request) {
        log.info("📥 LeetCode → MySQL + Vector DB 수집 요청: {}", request);

        try {
            int count = request.getCount() != null ? request.getCount() : 20;
            boolean useAi = request.getUseAiRewrite() != null ? request.getUseAiRewrite() : false;
            String difficulty = request.getDifficulty();

            int savedCount = crawlerService.fetchLeetCodeProblemsWithVectorDb(count, useAi, difficulty);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "LeetCode 문제 MySQL + Vector DB 수집 완료",
                    "savedCount", savedCount,
                    "difficulty", difficulty != null ? difficulty : "ALL",
                    "useAiRewrite", useAi,
                    "target", "MySQL + VectorDB"
            ));

        } catch (Exception e) {
            log.error("전체 수집 실패", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "수집 중 오류 발생: " + e.getMessage()
            ));
        }
    }

    // ===== Vector DB 검색 API =====

    /**
     * Vector DB에서 유사 문제 검색
     *
     * GET /api/admin/crawler/vectordb/search?query=동적 프로그래밍&topK=5
     */
    @GetMapping("/vectordb/search")
    public ResponseEntity<?> searchVectorDb(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {
        log.info("🔍 Vector DB 검색: query='{}', topK={}", query, topK);

        try {
            List<Document> results = vectorStoreService.searchSimilarProblems(query, topK);

            List<Map<String, Object>> resultList = results.stream()
                    .map(doc -> Map.of(
                            "id", doc.getId(),
                            "content", doc.getText().substring(0, Math.min(500, doc.getText().length())) + "...",
                            "metadata", doc.getMetadata()
                    ))
                    .toList();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "query", query,
                    "count", results.size(),
                    "results", resultList
            ));

        } catch (Exception e) {
            log.error("Vector DB 검색 실패", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "검색 중 오류 발생: " + e.getMessage()
            ));
        }
    }

    /**
     * Few-shot 학습용 예시 문제 검색
     *
     * GET /api/admin/crawler/vectordb/fewshot?topic=그래프&difficulty=SILVER&count=3
     */
    @GetMapping("/vectordb/fewshot")
    public ResponseEntity<?> getFewShotExamples(
            @RequestParam String topic,
            @RequestParam(defaultValue = "SILVER") String difficulty,
            @RequestParam(defaultValue = "3") int count) {
        log.info("🎯 Few-shot 예시 검색: topic='{}', difficulty={}, count={}",
                topic, difficulty, count);

        try {
            List<Document> results = vectorStoreService.getFewShotExamples(topic, difficulty, count);

            List<Map<String, Object>> resultList = results.stream()
                    .map(doc -> Map.of(
                            "id", doc.getId(),
                            "content", doc.getText(),
                            "metadata", doc.getMetadata()
                    ))
                    .toList();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "topic", topic,
                    "difficulty", difficulty,
                    "count", results.size(),
                    "examples", resultList
            ));

        } catch (Exception e) {
            log.error("Few-shot 검색 실패", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "검색 중 오류 발생: " + e.getMessage()
            ));
        }
    }

}
