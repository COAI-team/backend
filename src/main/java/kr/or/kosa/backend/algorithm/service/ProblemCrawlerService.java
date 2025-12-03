package kr.or.kosa.backend.algorithm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.algorithm.domain.AlgoProblem;
import kr.or.kosa.backend.algorithm.domain.ProblemDifficulty;
import kr.or.kosa.backend.algorithm.domain.ProblemSource;
import kr.or.kosa.backend.algorithm.domain.ProblemType;
import kr.or.kosa.backend.algorithm.dto.external.LeetCodeProblemDto;
import kr.or.kosa.backend.algorithm.dto.external.SolvedAcProblemDto;
import kr.or.kosa.backend.algorithm.mapper.AlgorithmProblemMapper;
import kr.or.kosa.backend.algorithm.service.external.LeetCodeApiClient;
import kr.or.kosa.backend.algorithm.service.external.SolvedAcApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ProblemRewriteService rewriteService;
    private final AlgorithmProblemMapper problemMapper;
    private final ObjectMapper objectMapper;

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

        AlgoProblem problem = AlgoProblem.builder()
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

        AlgoProblem problem = AlgoProblem.builder()
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
            List<AlgoProblem> problems = problemMapper.selectProblemsWithFilter(
                    0, 1, null, null, title
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
        int bojCount = problemMapper.countProblemsWithFilter(null, "BOJ", null);
        int leetCodeCount = problemMapper.countProblemsWithFilter(null, "CUSTOM", null);

        return String.format("""
                📊 크롤링 상태
                ━━━━━━━━━━━━━━━━━━━━━━
                전체 문제: %d개
                백준(BOJ): %d개
                LeetCode: %d개
                ━━━━━━━━━━━━━━━━━━━━━━
                """, totalProblems, bojCount, leetCodeCount);
    }
}
