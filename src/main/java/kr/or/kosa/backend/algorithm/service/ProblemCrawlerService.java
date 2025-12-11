package kr.or.kosa.backend.algorithm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.algorithm.dto.AlgoProblemDto;
import kr.or.kosa.backend.algorithm.dto.enums.ProblemDifficulty;
import kr.or.kosa.backend.algorithm.dto.enums.ProblemSource;
import kr.or.kosa.backend.algorithm.dto.enums.ProblemType;
import kr.or.kosa.backend.algorithm.dto.external.LeetCodeProblemDto;
import kr.or.kosa.backend.algorithm.dto.external.ProblemDocumentDto;
import kr.or.kosa.backend.algorithm.dto.external.SolvedAcProblemDto;
import kr.or.kosa.backend.algorithm.mapper.AlgorithmProblemMapper;
import kr.or.kosa.backend.algorithm.service.external.BojCrawler;
import kr.or.kosa.backend.algorithm.service.external.LeetCodeApiClient;
import kr.or.kosa.backend.algorithm.service.external.LeetCodeCrawler;
import kr.or.kosa.backend.algorithm.service.external.SolvedAcApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 문제 크롤링 통합 서비스
 * 외부 API에서 문제를 가져와 AI로 재서술하고 DB에 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemCrawlerService {

    private final SolvedAcApiClient solvedAcApiClient;
    private final LeetCodeApiClient leetCodeApiClient;
    private final LeetCodeCrawler leetCodeCrawler;
    private final ProblemRewriteService rewriteService;
    private final AlgorithmProblemMapper problemMapper;
    private final ObjectMapper objectMapper;
    private final BojCrawler bojCrawler;
    private final ProblemVectorStoreService vectorStoreService;

    /**
     * 백준 문제 일괄 가져오기
     *
     * @param query           검색 쿼리 (예: "*s", "tier:b")
     * @param totalCount      가져올 문제 수
     * @param useAiRewrite    AI 재서술 사용 여부
     * @return 저장 성공 개수
     */
    @Transactional
    public int fetchBojProblems(String query, int totalCount, boolean useAiRewrite) {
        log.info("🚀 백준 문제 크롤링 시작: query={}, totalCount={}, useAI={}",
                query, totalCount, useAiRewrite);

        AtomicInteger savedCount = new AtomicInteger(0);
        int page = 1;
        int maxPages = (totalCount / 50) + 1;

        while (savedCount.get() < totalCount && page <= maxPages) {
            List<SolvedAcProblemDto> problems = solvedAcApiClient.searchProblems(query, page);

            if (problems.isEmpty()) {
                log.info("더 이상 문제가 없습니다. 종료합니다.");
                break;
            }

            problems.stream()
                    .limit(totalCount - savedCount.get())
                    .forEach(problem -> {
                        try {
                            if (saveBojProblem(problem, useAiRewrite)) {
                                savedCount.incrementAndGet();
                                log.info("진행률: {}/{}", savedCount.get(), totalCount);
                            }
                        } catch (Exception e) {
                            log.error("문제 저장 실패: {}", problem.getTitleKo(), e);
                        }
                    });

            page++;

            // API Rate Limiting 방지
            sleep(1000);
        }

        log.info("✅ 백준 문제 크롤링 완료: {}개 저장", savedCount.get());
        return savedCount.get();
    }

    /**
     * 백준 문제 하나 저장
     */
    private boolean saveBojProblem(SolvedAcProblemDto dto, boolean useAiRewrite) {
        String title = String.format("[BOJ %d] %s",
                dto.getProblemId(),
                dto.getTitleKo() != null ? dto.getTitleKo() : dto.getTitle());

        // 중복 체크
        if (isDuplicate(title)) {
            log.debug("⏭️  이미 존재: {}", title);
            return false;
        }

        // 문제 설명 생성
        String description;
        if (useAiRewrite) {
            String tags = String.join(", ", dto.getKoreanTagNames());
            description = rewriteService.rewriteProblemDescriptionWithDelay(
                    title,
                    "백준 " + dto.getProblemId() + "번 문제",
                    dto.getDifficultyEnum(),
                    tags
            );
        } else {
            description = String.format("백준 %d번 문제\n난이도: %s",
                    dto.getProblemId(), dto.getDifficultyEnum());
        }

        // 태그를 JSON 문자열로 변환
        String tagsJson = convertTagsToJson(dto.getKoreanTagNames());

        AlgoProblemDto problem = AlgoProblemDto.builder()
                .algoProblemTitle(title)
                .algoProblemDescription(description)
                .algoProblemDifficulty(ProblemDifficulty.valueOf(dto.getDifficultyEnum()))
                .algoProblemSource(ProblemSource.BOJ)
                .problemType(ProblemType.ALGORITHM)
                .timelimit(1000)
                .memorylimit(256)
                .algoProblemTags(tagsJson)
                .algoProblemStatus(true)
                .build();

        int result = problemMapper.insertProblem(problem);
        log.info("✅ 저장 완료: {}", title);
        return result > 0;
    }

    /**
     * LeetCode 문제 일괄 가져오기
     *
     * @param totalCount   가져올 문제 수
     * @param useAiRewrite AI 재서술 사용 여부
     * @param difficulty   난이도 필터 (null이면 전체)
     * @return 저장 성공 개수
     */
    @Transactional
    public int fetchLeetCodeProblems(int totalCount, boolean useAiRewrite, String difficulty) {
        log.info("🚀 LeetCode 문제 크롤링 시작: totalCount={}, useAI={}, difficulty={}",
                totalCount, useAiRewrite, difficulty);

        AtomicInteger savedCount = new AtomicInteger(0);
        int iterations = (totalCount / 20) + 1;

        for (int i = 0; i < iterations && savedCount.get() < totalCount; i++) {
            List<LeetCodeProblemDto> problems = leetCodeApiClient.getProblems(20, null, difficulty);

            if (problems.isEmpty()) {
                log.info("더 이상 문제가 없습니다. 종료합니다.");
                break;
            }

            problems.stream()
                    .filter(p -> !Boolean.TRUE.equals(p.getIsPaidOnly()))  // 유료 문제 제외
                    .limit(totalCount - savedCount.get())
                    .forEach(problem -> {
                        try {
                            if (saveLeetCodeProblem(problem, useAiRewrite)) {
                                savedCount.incrementAndGet();
                                log.info("진행률: {}/{}", savedCount.get(), totalCount);
                            }
                        } catch (Exception e) {
                            log.error("문제 저장 실패: {}", problem.getTitle(), e);
                        }
                    });

            // API Rate Limiting 방지 (alfa-leetcode-api는 느릴 수 있음)
            sleep(2000);
        }

        log.info("✅ LeetCode 문제 크롤링 완료: {}개 저장", savedCount.get());
        return savedCount.get();
    }

    /**
     * LeetCode 문제 하나 저장
     */
    private boolean saveLeetCodeProblem(LeetCodeProblemDto dto, boolean useAiRewrite) {
        String title = String.format("[LeetCode %s] %s",
                dto.getQuestionId(), dto.getTitle());

        // 중복 체크
        if (isDuplicate(title)) {
            log.debug("⏭️  이미 존재: {}", title);
            return false;
        }

        // 문제 설명 생성
        String description;
        if (useAiRewrite) {
            String tags = String.join(", ", dto.getTagNames());
            description = rewriteService.rewriteProblemDescriptionWithDelay(
                    title,
                    "LeetCode " + dto.getQuestionId() + "번 문제",
                    dto.getDifficultyEnum(),
                    tags
            );
        } else {
            description = String.format("LeetCode %s번 문제\n난이도: %s",
                    dto.getQuestionId(), dto.getDifficulty());
        }

        // 태그를 JSON 문자열로 변환
        String tagsJson = convertTagsToJson(dto.getTagNames());

        AlgoProblemDto problem = AlgoProblemDto.builder()
                .algoProblemTitle(title)
                .algoProblemDescription(description)
                .algoProblemDifficulty(ProblemDifficulty.valueOf(dto.getDifficultyEnum()))
                .algoProblemSource(ProblemSource.CUSTOM)  // LeetCode는 CUSTOM
                .problemType(ProblemType.ALGORITHM)
                .timelimit(2000)
                .memorylimit(512)
                .algoProblemTags(tagsJson)
                .algoProblemStatus(true)
                .build();

        int result = problemMapper.insertProblem(problem);
        log.info("✅ 저장 완료: {}", title);
        return result > 0;
    }

    /**
     * 중복 체크 (제목으로)
     */
    private boolean isDuplicate(String title) {
        try {
            List<AlgoProblemDto> problems = problemMapper.selectProblemsWithFilter(
                    0, 1, null, null, title, null
            );
            return !problems.isEmpty();
        } catch (Exception e) {
            log.warn("중복 체크 실패: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 태그 리스트를 JSON 문자열로 변환
     */
    private String convertTagsToJson(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException e) {
            log.error("태그 JSON 변환 실패", e);
            return "[]";
        }
    }

    /**
     * 지연 시간 추가 (Rate Limiting 방지)
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Sleep 중단됨");
        }
    }

    /**
     * 크롤링 상태 조회
     */
    public String getCrawlerStatus() {
        int totalProblems = problemMapper.countAllProblems();
        int bojCount = problemMapper.countProblemsWithFilter(null, "BOJ", null, null);
        int leetCodeCount = problemMapper.countProblemsWithFilter(null, "CUSTOM", null, null);

        return String.format("""
                📊 크롤링 상태
                ━━━━━━━━━━━━━━━━━━━━━━
                전체 문제: %d개
                백준(BOJ): %d개
                LeetCode: %d개
                ━━━━━━━━━━━━━━━━━━━━━━
                """, totalProblems, bojCount, leetCodeCount);
    }

    // ===== Vector DB 전용 메서드 =====

    /**
     * BOJ 문제를 Vector DB에 수집 (RAG용)
     * MySQL 저장 없이 Vector DB에만 저장
     *
     * @param query      검색 쿼리 (예: "*s", "tier:g")
     * @param totalCount 수집할 문제 수
     * @return 저장된 문제 수
     */
    public int collectBojToVectorDb(String query, int totalCount) {
        log.info("🚀 BOJ → Vector DB 수집 시작: query={}, count={}", query, totalCount);

        List<ProblemDocumentDto> documents = new ArrayList<>();
        int page = 1;
        int maxPages = (totalCount / 50) + 1;

        while (documents.size() < totalCount && page <= maxPages) {
            List<SolvedAcProblemDto> problems = solvedAcApiClient.searchProblems(query, page);

            if (problems.isEmpty()) {
                log.info("더 이상 문제가 없습니다.");
                break;
            }

            for (SolvedAcProblemDto problem : problems) {
                if (documents.size() >= totalCount) break;

                // 한국어 제목이 없는 문제는 건너뛰기 (영어 전용 문제 필터링)
                if (problem.getTitleKo() == null || problem.getTitleKo().isBlank()) {
                    log.debug("⏭️ 영어 전용 문제 건너뛰기: {} (problemId: {})",
                            problem.getTitle(), problem.getProblemId());
                    continue;
                }

                try {
                    ProblemDocumentDto doc = bojCrawler.crawlProblemDetail(problem);
                    documents.add(doc);
                    log.info("📥 크롤링 완료: {}/{} - {}",
                            documents.size(), totalCount, doc.getTitle());

                    // Rate Limiting 방지 (BOJ 크롤링)
                    sleep(500);
                } catch (Exception e) {
                    log.error("크롤링 실패: {}", problem.getTitleKo(), e);
                }
            }

            page++;
            sleep(1000); // solved.ac API Rate Limiting
        }

        // Vector DB에 일괄 저장
        if (!documents.isEmpty()) {
            int savedCount = vectorStoreService.storeProblems(documents);
            log.info("✅ Vector DB 저장 완료: {}개 문제", savedCount);
            return savedCount;
        }

        return 0;
    }

    /**
     * BOJ 문제를 MySQL + Vector DB 모두에 저장
     *
     * @param query        검색 쿼리
     * @param totalCount   수집할 문제 수
     * @param useAiRewrite AI 재서술 사용 여부
     * @return 저장된 문제 수
     */
    @Transactional
    public int fetchBojProblemsWithVectorDb(String query, int totalCount, boolean useAiRewrite) {
        log.info("🚀 BOJ → MySQL + Vector DB 수집 시작: query={}, count={}, useAI={}",
                query, totalCount, useAiRewrite);

        AtomicInteger savedCount = new AtomicInteger(0);
        List<ProblemDocumentDto> vectorDocs = new ArrayList<>();
        int page = 1;
        int maxPages = (totalCount / 50) + 1;

        while (savedCount.get() < totalCount && page <= maxPages) {
            List<SolvedAcProblemDto> problems = solvedAcApiClient.searchProblems(query, page);

            if (problems.isEmpty()) {
                log.info("더 이상 문제가 없습니다.");
                break;
            }

            for (SolvedAcProblemDto problem : problems) {
                if (savedCount.get() >= totalCount) break;

                try {
                    // 1. MySQL에 저장
                    if (saveBojProblem(problem, useAiRewrite)) {
                        savedCount.incrementAndGet();

                        // 2. Vector DB용 문서 크롤링
                        ProblemDocumentDto doc = bojCrawler.crawlProblemDetail(problem);
                        vectorDocs.add(doc);

                        log.info("진행률: {}/{}", savedCount.get(), totalCount);
                        sleep(500); // BOJ 크롤링 Rate Limiting
                    }
                } catch (Exception e) {
                    log.error("문제 저장 실패: {}", problem.getTitleKo(), e);
                }
            }

            page++;
            sleep(1000);
        }

        // Vector DB에 일괄 저장
        if (!vectorDocs.isEmpty()) {
            vectorStoreService.storeProblems(vectorDocs);
            log.info("✅ Vector DB 저장 완료: {}개 문제", vectorDocs.size());
        }

        log.info("✅ 전체 저장 완료: MySQL={}개, Vector DB={}개",
                savedCount.get(), vectorDocs.size());
        return savedCount.get();
    }

    /**
     * LeetCode 문제를 Vector DB에 수집 (RAG용)
     * MySQL 저장 없이 Vector DB에만 저장
     *
     * @param totalCount 수집할 문제 수
     * @param difficulty 난이도 필터 (null이면 전체)
     * @return 저장된 문제 수
     */
    public int collectLeetCodeToVectorDb(int totalCount, String difficulty) {
        log.info("🚀 LeetCode → Vector DB 수집 시작: count={}, difficulty={}",
                totalCount, difficulty);

        List<ProblemDocumentDto> documents = new ArrayList<>();
        int iterations = (totalCount / 20) + 1;

        for (int i = 0; i < iterations && documents.size() < totalCount; i++) {
            List<LeetCodeProblemDto> problems = leetCodeApiClient.getProblems(20, null, difficulty);

            if (problems.isEmpty()) {
                log.info("더 이상 문제가 없습니다.");
                break;
            }

            for (LeetCodeProblemDto problem : problems) {
                if (documents.size() >= totalCount) break;

                // 유료 문제 제외
                if (Boolean.TRUE.equals(problem.getIsPaidOnly())) {
                    continue;
                }

                try {
                    ProblemDocumentDto doc = leetCodeCrawler.crawlProblemDetail(problem);
                    documents.add(doc);
                    log.info("📥 크롤링 완료: {}/{} - {}",
                            documents.size(), totalCount, doc.getTitle());

                    // Rate Limiting 방지
                    sleep(1000);
                } catch (Exception e) {
                    log.error("크롤링 실패: {}", problem.getTitle(), e);
                }
            }

            sleep(2000); // alfa-leetcode-api Rate Limiting
        }

        // Vector DB에 일괄 저장
        if (!documents.isEmpty()) {
            int savedCount = vectorStoreService.storeProblems(documents);
            log.info("✅ Vector DB 저장 완료: {}개 문제", savedCount);
            return savedCount;
        }

        return 0;
    }

    /**
     * LeetCode 문제를 MySQL + Vector DB 모두에 저장
     *
     * @param totalCount   수집할 문제 수
     * @param useAiRewrite AI 재서술 사용 여부
     * @param difficulty   난이도 필터 (null이면 전체)
     * @return 저장된 문제 수
     */
    @Transactional
    public int fetchLeetCodeProblemsWithVectorDb(int totalCount, boolean useAiRewrite, String difficulty) {
        log.info("🚀 LeetCode → MySQL + Vector DB 수집 시작: count={}, useAI={}, difficulty={}",
                totalCount, useAiRewrite, difficulty);

        AtomicInteger savedCount = new AtomicInteger(0);
        List<ProblemDocumentDto> vectorDocs = new ArrayList<>();
        int iterations = (totalCount / 20) + 1;

        for (int i = 0; i < iterations && savedCount.get() < totalCount; i++) {
            List<LeetCodeProblemDto> problems = leetCodeApiClient.getProblems(20, null, difficulty);

            if (problems.isEmpty()) {
                log.info("더 이상 문제가 없습니다.");
                break;
            }

            for (LeetCodeProblemDto problem : problems) {
                if (savedCount.get() >= totalCount) break;

                // 유료 문제 제외
                if (Boolean.TRUE.equals(problem.getIsPaidOnly())) {
                    continue;
                }

                try {
                    // 1. MySQL에 저장
                    if (saveLeetCodeProblem(problem, useAiRewrite)) {
                        savedCount.incrementAndGet();

                        // 2. Vector DB용 문서 크롤링
                        ProblemDocumentDto doc = leetCodeCrawler.crawlProblemDetail(problem);
                        vectorDocs.add(doc);

                        log.info("진행률: {}/{}", savedCount.get(), totalCount);
                        sleep(1000);
                    }
                } catch (Exception e) {
                    log.error("문제 저장 실패: {}", problem.getTitle(), e);
                }
            }

            sleep(2000);
        }

        // Vector DB에 일괄 저장
        if (!vectorDocs.isEmpty()) {
            vectorStoreService.storeProblems(vectorDocs);
            log.info("✅ Vector DB 저장 완료: {}개 문제", vectorDocs.size());
        }

        log.info("✅ 전체 저장 완료: MySQL={}개, Vector DB={}개",
                savedCount.get(), vectorDocs.size());
        return savedCount.get();
    }

    // ===== 배치 크롤링 메서드 (유사도 검사용 Vector DB 구축) =====

    /**
     * solved.ac 태그명 매핑 (24개 알고리즘 토픽)
     */
    private static final java.util.Map<String, String> TOPIC_TAG_MAP = java.util.Map.ofEntries(
            java.util.Map.entry("implementation", "implementation"),
            java.util.Map.entry("greedy", "greedy"),
            java.util.Map.entry("sorting", "sorting"),
            java.util.Map.entry("binary_search", "binary_search"),
            java.util.Map.entry("bruteforcing", "bruteforcing"),
            java.util.Map.entry("bfs", "bfs"),
            java.util.Map.entry("dfs", "dfs"),
            java.util.Map.entry("dp", "dp"),
            java.util.Map.entry("divide_and_conquer", "divide_and_conquer"),
            java.util.Map.entry("backtracking", "backtracking"),
            java.util.Map.entry("stack", "data_structures"),  // stack은 data_structures로 대체
            java.util.Map.entry("queue", "data_structures"),  // queue도 data_structures로 대체
            java.util.Map.entry("hashing", "hashing"),
            java.util.Map.entry("priority_queue", "priority_queue"),
            java.util.Map.entry("graphs", "graphs"),
            java.util.Map.entry("shortest_path", "shortest_path"),
            java.util.Map.entry("trees", "trees"),
            java.util.Map.entry("disjoint_set", "disjoint_set"),
            java.util.Map.entry("string", "string"),
            java.util.Map.entry("math", "math"),
            java.util.Map.entry("bitmask", "bitmask"),
            java.util.Map.entry("two_pointer", "two_pointer"),
            java.util.Map.entry("sliding_window", "sliding_window"),
            java.util.Map.entry("simulation", "simulation")
    );

    /**
     * 난이도별 solved.ac 티어 쿼리 매핑
     */
    private static final java.util.Map<String, String> DIFFICULTY_TIER_MAP = java.util.Map.of(
            "BRONZE", "b1..b5",
            "SILVER", "s1..s5",
            "GOLD", "g1..g5",
            "PLATINUM", "p1..p5"
    );

    /**
     * BOJ 문제를 난이도/토픽별로 배치 크롤링하여 Vector DB에 저장
     * 4 난이도 × 24 토픽 × N 문제 = 총 4*24*N 문제 수집
     *
     * @param problemsPerCategory 카테고리당 수집할 문제 수
     * @param progressCallback    진행률 콜백 (nullable)
     * @return 총 저장된 문제 수
     */
    public int collectBojBatchToVectorDb(int problemsPerCategory,
                                         java.util.function.Consumer<BatchProgress> progressCallback) {
        log.info("🚀 BOJ 배치 크롤링 시작 - 카테고리당 {}문제", problemsPerCategory);

        List<String> difficulties = List.of("BRONZE", "SILVER", "GOLD", "PLATINUM");
        List<String> topics = new ArrayList<>(TOPIC_TAG_MAP.keySet());

        int totalCategories = difficulties.size() * topics.size();
        int currentCategory = 0;
        int totalSaved = 0;

        for (String difficulty : difficulties) {
            String tierQuery = DIFFICULTY_TIER_MAP.get(difficulty);

            for (String topic : topics) {
                currentCategory++;
                String solvedAcTag = TOPIC_TAG_MAP.get(topic);

                // solved.ac 쿼리 형식: "tier:s1..s5 #dp"
                String query = String.format("tier:%s #%s", tierQuery, solvedAcTag);

                log.info("📥 [{}/{}] 크롤링 중: {} - {} (query: {})",
                        currentCategory, totalCategories, difficulty, topic, query);

                // 진행률 콜백
                if (progressCallback != null) {
                    progressCallback.accept(new BatchProgress(
                            currentCategory, totalCategories,
                            difficulty, topic, totalSaved
                    ));
                }

                try {
                    int saved = collectBojToVectorDb(query, problemsPerCategory);
                    totalSaved += saved;
                    log.info("✅ {} - {}: {}개 저장 (누적: {})", difficulty, topic, saved, totalSaved);
                } catch (Exception e) {
                    log.error("❌ {} - {} 크롤링 실패: {}", difficulty, topic, e.getMessage());
                }

                // 카테고리 간 딜레이 (Rate Limiting 방지)
                sleep(2000);
            }
        }

        log.info("🎉 BOJ 배치 크롤링 완료 - 총 {}개 문제 저장", totalSaved);
        return totalSaved;
    }

    /**
     * 특정 난이도의 모든 토픽 크롤링
     *
     * @param difficulty          난이도 (BRONZE, SILVER, GOLD, PLATINUM)
     * @param problemsPerCategory 카테고리당 수집할 문제 수
     * @return 저장된 문제 수
     */
    public int collectBojByDifficulty(String difficulty, int problemsPerCategory) {
        log.info("🚀 BOJ 크롤링 시작 - 난이도: {}, 토픽당 {}문제", difficulty, problemsPerCategory);

        String tierQuery = DIFFICULTY_TIER_MAP.get(difficulty);
        if (tierQuery == null) {
            log.error("❌ 잘못된 난이도: {}", difficulty);
            return 0;
        }

        int totalSaved = 0;

        for (java.util.Map.Entry<String, String> entry : TOPIC_TAG_MAP.entrySet()) {
            String topic = entry.getKey();
            String solvedAcTag = entry.getValue();

            String query = String.format("tier:%s #%s", tierQuery, solvedAcTag);
            log.info("📥 크롤링 중: {} - {} (query: {})", difficulty, topic, query);

            try {
                int saved = collectBojToVectorDb(query, problemsPerCategory);
                totalSaved += saved;
                log.info("✅ {} - {}: {}개 저장", difficulty, topic, saved);
            } catch (Exception e) {
                log.error("❌ {} - {} 크롤링 실패: {}", difficulty, topic, e.getMessage());
            }

            sleep(2000);
        }

        log.info("🎉 {} 난이도 크롤링 완료 - 총 {}개 문제 저장", difficulty, totalSaved);
        return totalSaved;
    }

    /**
     * 특정 토픽의 모든 난이도 크롤링
     *
     * @param topic               토픽 (dp, greedy, bfs 등)
     * @param problemsPerCategory 난이도당 수집할 문제 수
     * @return 저장된 문제 수
     */
    public int collectBojByTopic(String topic, int problemsPerCategory) {
        log.info("🚀 BOJ 크롤링 시작 - 토픽: {}, 난이도당 {}문제", topic, problemsPerCategory);

        String solvedAcTag = TOPIC_TAG_MAP.get(topic);
        if (solvedAcTag == null) {
            log.error("❌ 잘못된 토픽: {}", topic);
            return 0;
        }

        int totalSaved = 0;

        for (java.util.Map.Entry<String, String> entry : DIFFICULTY_TIER_MAP.entrySet()) {
            String difficulty = entry.getKey();
            String tierQuery = entry.getValue();

            String query = String.format("tier:%s #%s", tierQuery, solvedAcTag);
            log.info("📥 크롤링 중: {} - {} (query: {})", difficulty, topic, query);

            try {
                int saved = collectBojToVectorDb(query, problemsPerCategory);
                totalSaved += saved;
                log.info("✅ {} - {}: {}개 저장", difficulty, topic, saved);
            } catch (Exception e) {
                log.error("❌ {} - {} 크롤링 실패: {}", difficulty, topic, e.getMessage());
            }

            sleep(2000);
        }

        log.info("🎉 {} 토픽 크롤링 완료 - 총 {}개 문제 저장", topic, totalSaved);
        return totalSaved;
    }

    /**
     * 배치 크롤링 진행률 클래스
     */
    public static class BatchProgress {
        private final int currentCategory;
        private final int totalCategories;
        private final String difficulty;
        private final String topic;
        private final int totalSaved;

        public BatchProgress(int currentCategory, int totalCategories,
                             String difficulty, String topic, int totalSaved) {
            this.currentCategory = currentCategory;
            this.totalCategories = totalCategories;
            this.difficulty = difficulty;
            this.topic = topic;
            this.totalSaved = totalSaved;
        }

        public int getCurrentCategory() { return currentCategory; }
        public int getTotalCategories() { return totalCategories; }
        public String getDifficulty() { return difficulty; }
        public String getTopic() { return topic; }
        public int getTotalSaved() { return totalSaved; }
        public int getPercentage() { return (currentCategory * 100) / totalCategories; }
    }
}
