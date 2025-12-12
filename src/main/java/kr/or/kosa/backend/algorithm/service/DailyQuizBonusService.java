package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.pay.service.PointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 일일 퀴즈(오늘의 문제) 최초 AC 보너스 지급 서비스
 * Redis Set으로 선착순 3명을 결정하고, 기존 클라이언트 로직과는 독립적으로 동작
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DailyQuizBonusService {

    private static final int DAILY_QUIZ_FIRST_SOLVER_BONUS = 50;
    private static final int DAILY_QUIZ_FIRST_SOLVER_LIMIT = 3;
    private static final long TTL_SECONDS = 172_800L; // 48 hours
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final StringRedisTemplate stringRedisTemplate;
    private final PointService pointService;
    private final DailyMissionService dailyMissionService;

    /**
     * 일일 퀴즈 AC 시 호출. 오늘의 문제이고 선착순 3명 안에 들면 50P 지급.
     */
    public void handleDailyQuizSolved(Long userId, Long problemId, LocalDate solvedDate) {
        try {
            if (userId == null || problemId == null || solvedDate == null) return;

            // 오늘의 문제인지 확인
            Long todayMissionProblemId = dailyMissionService.getTodaySolveMissionProblemId(userId);
            if (todayMissionProblemId == null || !todayMissionProblemId.equals(problemId)) {
                return; // 오늘 문제풀이가 아니면 보너스 없음
            }

            String dateStr = solvedDate.format(DATE_FMT);
            String key = String.format("dailyquiz:%s:%d:solvers", dateStr, problemId);
            String userVal = String.valueOf(userId);

            Long added = stringRedisTemplate.opsForSet().add(key, userVal);
            if (added == null || added == 0L) {
                return; // 이미 참여자는 무시
            }

            Long count = stringRedisTemplate.opsForSet().size(key);

            // TTL 설정 (없을 경우만)
            Long ttl = stringRedisTemplate.getExpire(key);
            if (ttl == null || ttl == -1L) {
                stringRedisTemplate.expire(key, TTL_SECONDS, TimeUnit.SECONDS);
            }

            if (count != null && count <= DAILY_QUIZ_FIRST_SOLVER_LIMIT) {
                pointService.addRewardPoint(
                        userId,
                        DAILY_QUIZ_FIRST_SOLVER_BONUS,
                        "DAILY_QUIZ_FIRST_SOLVER"
                );
                log.info("DailyQuiz 보너스 지급 userId={}, problemId={}, count={}", userId, problemId, count);
            } else {
                log.debug("DailyQuiz 보너스 마감: userId={}, problemId={}, count={}", userId, problemId, count);
            }
        } catch (Exception e) {
            // Redis 오류 등은 로깅만 처리 (포인트 줄로직에는 영향 X)
            log.warn("DailyQuiz 보너스 처리 예외 (무시): userId={}, problemId={}, msg={}",
                    userId, problemId, e.getMessage());
        }
    }

    /**
     * 오늘의 문제 보너스 상태 조회 (n/limit, 내가 받을 수 있는지)
     */
    public BonusStatus getBonusStatus(Long userId, Long problemId, LocalDate solvedDate) {
        if (problemId == null || solvedDate == null) {
            return new BonusStatus(0L, DAILY_QUIZ_FIRST_SOLVER_LIMIT, false);
        }

        String dateStr = solvedDate.format(DATE_FMT);
        String key = String.format("dailyquiz:%s:%d:solvers", dateStr, problemId);
        Long count = stringRedisTemplate.opsForSet().size(key);
        boolean alreadyReceived = Boolean.TRUE.equals(
                stringRedisTemplate.opsForSet().isMember(key, String.valueOf(userId))
        );

        long currentCount = count != null ? count : 0L;
        boolean eligible = !alreadyReceived && currentCount < DAILY_QUIZ_FIRST_SOLVER_LIMIT;

        return new BonusStatus(currentCount, DAILY_QUIZ_FIRST_SOLVER_LIMIT, eligible);
    }

    public record BonusStatus(Long currentCount, int limit, boolean eligible) {}
}
