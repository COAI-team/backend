package kr.or.kosa.backend.algorithm.controller;

import kr.or.kosa.backend.algorithm.dto.request.BojCrawlRequestDto;
import kr.or.kosa.backend.algorithm.dto.request.LeetCodeCrawlRequestDto;
import kr.or.kosa.backend.algorithm.dto.request.VectorDbCrawlRequestDto;
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
    public ResponseEntity<?> crawlBojProblems(@RequestBody BojCrawlRequestDto request) {
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
    public ResponseEntity<?> crawlLeetCodeProblems(@RequestBody LeetCodeCrawlRequestDto request) {
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
    public ResponseEntity<?> collectBojToVectorDb(@RequestBody VectorDbCrawlRequestDto request) {
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
    public ResponseEntity<?> crawlBojWithVectorDb(@RequestBody BojCrawlRequestDto request) {
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
    public ResponseEntity<?> collectLeetCodeToVectorDb(@RequestBody LeetCodeCrawlRequestDto request) {
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
    public ResponseEntity<?> crawlLeetCodeWithVectorDb(@RequestBody LeetCodeCrawlRequestDto request) {
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

    // ===== 배치 크롤링 API (유사도 검사용 Vector DB 구축) =====

    /**
     * BOJ 문제 배치 크롤링 (4난이도 × 24토픽 × N문제)
     * 유사도 검사용 Vector DB 전체 구축
     *
     * POST /algo/crawler/vectordb/boj/batch
     * Body: {
     *   "problemsPerCategory": 5   // 카테고리당 수집할 문제 수 (기본: 5)
     * }
     *
     * 예상 수집량: 4 × 24 × 5 = 480문제
     * 예상 소요 시간: ~30분 (Rate Limiting 고려)
     */
    @PostMapping("/vectordb/boj/batch")
    public ResponseEntity<?> batchCrawlBojToVectorDb(
            @RequestBody(required = false) Map<String, Integer> request) {
        int problemsPerCategory = 5;
        if (request != null && request.containsKey("problemsPerCategory")) {
            problemsPerCategory = request.get("problemsPerCategory");
        }

        log.info("📥 BOJ 배치 크롤링 요청 - 카테고리당 {}문제", problemsPerCategory);

        try {
            final int count = problemsPerCategory;
            int savedCount = crawlerService.collectBojBatchToVectorDb(count, null);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "BOJ 배치 크롤링 완료",
                    "savedCount", savedCount,
                    "problemsPerCategory", count,
                    "expectedTotal", 4 * 24 * count,
                    "target", "VectorDB"
            ));

        } catch (Exception e) {
            log.error("배치 크롤링 실패", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "배치 크롤링 중 오류 발생: " + e.getMessage()
            ));
        }
    }

    /**
     * 특정 난이도의 모든 토픽 크롤링
     *
     * POST /algo/crawler/vectordb/boj/difficulty
     * Body: {
     *   "difficulty": "SILVER",      // BRONZE, SILVER, GOLD, PLATINUM
     *   "problemsPerCategory": 5
     * }
     */
    @PostMapping("/vectordb/boj/difficulty")
    public ResponseEntity<?> crawlBojByDifficulty(@RequestBody Map<String, Object> request) {
        String difficulty = (String) request.getOrDefault("difficulty", "SILVER");
        int problemsPerCategory = (Integer) request.getOrDefault("problemsPerCategory", 5);

        log.info("📥 BOJ 난이도별 크롤링 요청 - 난이도: {}, 토픽당 {}문제", difficulty, problemsPerCategory);

        try {
            int savedCount = crawlerService.collectBojByDifficulty(difficulty, problemsPerCategory);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", difficulty + " 난이도 크롤링 완료",
                    "savedCount", savedCount,
                    "difficulty", difficulty,
                    "problemsPerCategory", problemsPerCategory,
                    "target", "VectorDB"
            ));

        } catch (Exception e) {
            log.error("난이도별 크롤링 실패", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "크롤링 중 오류 발생: " + e.getMessage()
            ));
        }
    }

    /**
     * 특정 토픽의 모든 난이도 크롤링
     *
     * POST /algo/crawler/vectordb/boj/topic
     * Body: {
     *   "topic": "dp",               // dp, greedy, bfs, dfs, etc.
     *   "problemsPerCategory": 5
     * }
     */
    @PostMapping("/vectordb/boj/topic")
    public ResponseEntity<?> crawlBojByTopic(@RequestBody Map<String, Object> request) {
        String topic = (String) request.getOrDefault("topic", "dp");
        int problemsPerCategory = (Integer) request.getOrDefault("problemsPerCategory", 5);

        log.info("📥 BOJ 토픽별 크롤링 요청 - 토픽: {}, 난이도당 {}문제", topic, problemsPerCategory);

        try {
            int savedCount = crawlerService.collectBojByTopic(topic, problemsPerCategory);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", topic + " 토픽 크롤링 완료",
                    "savedCount", savedCount,
                    "topic", topic,
                    "problemsPerCategory", problemsPerCategory,
                    "target", "VectorDB"
            ));

        } catch (Exception e) {
            log.error("토픽별 크롤링 실패", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "크롤링 중 오류 발생: " + e.getMessage()
            ));
        }
    }

    // ===== Vector DB 통계 API =====

    /**
     * Vector DB 컬렉션 통계 조회
     * 난이도별, 토픽별 문서 수 현황 파악
     *
     * GET /algo/crawler/vectordb/stats?expectedPerCategory=5
     */
    @GetMapping("/vectordb/stats")
    public ResponseEntity<?> getVectorDbStats(
            @RequestParam(defaultValue = "5") int expectedPerCategory) {
        log.info("📊 Vector DB 통계 조회 요청: expectedPerCategory={}", expectedPerCategory);

        try {
            ProblemVectorStoreService.VectorDbStats stats = vectorStoreService.getCollectionStats();

            if (stats.getError() != null) {
                return ResponseEntity.status(500).body(Map.of(
                        "success", false,
                        "message", "통계 조회 중 오류 발생: " + stats.getError()
                ));
            }

            // 기대 수집량 계산: 4 난이도 × 23 토픽 × expectedPerCategory
            int expectedTotal = 4 * 23 * expectedPerCategory;

            // 부족한 카테고리 목록
            var missingCategories = stats.getMissingCategories(expectedPerCategory);

            // Map.of()는 최대 10개까지만 지원하므로 HashMap 사용
            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("success", true);
            response.put("totalDocuments", stats.getTotalDocuments());
            response.put("expectedTotal", expectedTotal);
            response.put("completionRate", String.format("%.1f%%",
                    (double) stats.getTotalDocuments() / expectedTotal * 100));
            response.put("byDifficulty", stats.getByDifficulty());
            response.put("byTopic", stats.getByTopic());
            response.put("bySource", stats.getBySource());
            response.put("byCombination", stats.getByCombination());
            response.put("missingCategories", missingCategories);
            response.put("missingCount", missingCategories.size());
            response.put("summary", Map.of(
                    "totalCategories", 4 * 23,
                    "completeCategories", (4 * 23) - missingCategories.size(),
                    "incompleteCategories", missingCategories.size()
            ));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Vector DB 통계 조회 실패", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "통계 조회 중 오류 발생: " + e.getMessage()
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

    // ===== Vector DB 관리 API (삭제) =====

    /**
     * 영어 문제 조회 (삭제 전 확인용)
     *
     * GET /algo/crawler/vectordb/english?topK=100
     */
    @GetMapping("/vectordb/english")
    public ResponseEntity<?> findEnglishProblems(
            @RequestParam(defaultValue = "100") int topK) {
        log.info("🔍 영어 문제 검색: topK={}", topK);

        try {
            List<Document> englishDocs = vectorStoreService.findEnglishProblems(topK);

            List<Map<String, Object>> resultList = englishDocs.stream()
                    .map(doc -> Map.of(
                            "id", doc.getId(),
                            "title", doc.getMetadata().getOrDefault("title", "Unknown"),
                            "source", doc.getMetadata().getOrDefault("source", "Unknown"),
                            "externalId", doc.getMetadata().getOrDefault("externalId", "Unknown"),
                            "contentPreview", doc.getText().substring(0, Math.min(200, doc.getText().length())) + "..."
                    ))
                    .toList();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "영어 문제 검색 완료",
                    "count", englishDocs.size(),
                    "problems", resultList
            ));

        } catch (Exception e) {
            log.error("영어 문제 검색 실패", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "검색 중 오류 발생: " + e.getMessage()
            ));
        }
    }

    /**
     * 영어 문제 일괄 삭제
     *
     * DELETE /algo/crawler/vectordb/english?searchLimit=500
     */
    @DeleteMapping("/vectordb/english")
    public ResponseEntity<?> deleteEnglishProblems(
            @RequestParam(defaultValue = "500") int searchLimit) {
        log.info("🗑️ 영어 문제 삭제 요청: searchLimit={}", searchLimit);

        try {
            int deletedCount = vectorStoreService.deleteEnglishProblems(searchLimit);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "영어 문제 삭제 완료",
                    "deletedCount", deletedCount
            ));

        } catch (Exception e) {
            log.error("영어 문제 삭제 실패", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "삭제 중 오류 발생: " + e.getMessage()
            ));
        }
    }

    /**
     * 특정 문서 ID로 삭제
     *
     * DELETE /algo/crawler/vectordb/documents
     * Body: {
     *   "documentIds": ["uuid1", "uuid2", ...]
     * }
     */
    @DeleteMapping("/vectordb/documents")
    public ResponseEntity<?> deleteDocuments(@RequestBody Map<String, List<String>> request) {
        List<String> documentIds = request.get("documentIds");

        if (documentIds == null || documentIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "삭제할 문서 ID가 필요합니다"
            ));
        }

        log.info("🗑️ 문서 삭제 요청: {}개 문서", documentIds.size());

        try {
            int deletedCount = vectorStoreService.deleteDocuments(documentIds);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "문서 삭제 완료",
                    "deletedCount", deletedCount
            ));

        } catch (Exception e) {
            log.error("문서 삭제 실패", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "삭제 중 오류 발생: " + e.getMessage()
            ));
        }
    }

    /**
     * Vector DB 전체 초기화 (주의: 모든 문서 삭제)
     *
     * DELETE /algo/crawler/vectordb/all?confirm=true
     */
    @DeleteMapping("/vectordb/all")
    public ResponseEntity<?> deleteAllProblems(
            @RequestParam(defaultValue = "false") boolean confirm) {
        log.warn("⚠️ Vector DB 전체 삭제 요청: confirm={}", confirm);

        if (!confirm) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "전체 삭제를 수행하려면 confirm=true 파라미터가 필요합니다"
            ));
        }

        try {
            int deletedCount = vectorStoreService.deleteAllProblems(true);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Vector DB 전체 삭제 완료",
                    "deletedCount", deletedCount,
                    "warning", "모든 문서가 삭제되었습니다"
            ));

        } catch (Exception e) {
            log.error("전체 삭제 실패", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "삭제 중 오류 발생: " + e.getMessage()
            ));
        }
    }

}
