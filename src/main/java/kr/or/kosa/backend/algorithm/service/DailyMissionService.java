package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.dto.DailyMissionDto;
import kr.or.kosa.backend.algorithm.dto.UserAlgoLevelDto;
import kr.or.kosa.backend.algorithm.dto.enums.AlgoLevel;
import kr.or.kosa.backend.algorithm.dto.enums.MissionType;
import kr.or.kosa.backend.algorithm.dto.enums.ProblemDifficulty;
import kr.or.kosa.backend.algorithm.mapper.AlgorithmSubmissionMapper;
import kr.or.kosa.backend.algorithm.mapper.DailyMissionMapper;
import kr.or.kosa.backend.pay.entity.Subscription;
import kr.or.kosa.backend.pay.repository.SubscriptionMapper;
import kr.or.kosa.backend.pay.service.PointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 데일리 미션 서비스
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DailyMissionService {

    private final DailyMissionMapper missionMapper;
    private final AlgorithmSubmissionMapper submissionMapper;  // 잔디 캘린더용
    private final PointService pointService;
    private final RateLimitService rateLimitService;
    private final SubscriptionMapper subscriptionMapper;
    private final ProblemPoolService problemPoolService;  // Pool에서 문제 가져오기용

    /**
     * 오늘의 미션 조회 (없으면 생성)
     */
    @Transactional
    public List<DailyMissionDto> getTodayMissions(Long userId) {
        LocalDate today = LocalDate.now();
        List<DailyMissionDto> missions = missionMapper.findTodayMissions(userId, today);

        // 미션이 없으면 생성
        if (missions.isEmpty()) {
            createDailyMissionsForUser(userId);
            missions = missionMapper.findTodayMissions(userId, today);
        }

        return missions;
    }

    /**
     * 특정 사용자에 대한 오늘 미션 생성
     */
    @Transactional
    public void createDailyMissionsForUser(Long userId) {
        LocalDate today = LocalDate.now();

        // 이미 미션이 있는지 확인
        List<DailyMissionDto> existing = missionMapper.findTodayMissions(userId, today);
        if (!existing.isEmpty()) {
            log.debug("사용자 {} 오늘 미션이 이미 존재합니다.", userId);
            return;
        }

        // 사용자 레벨 조회 (없으면 생성)
        UserAlgoLevelDto userLevel = getOrCreateUserLevel(userId);
        AlgoLevel level = userLevel.getAlgoLevel();
        String difficulty = level.getMatchingDifficulty().name();
        int rewardPoints = level.getRewardPoints();

        // 오늘 같은 난이도로 이미 할당된 문제가 있는지 확인 (같은 레벨 유저에게 같은 문제 배정)
        Long problemId = missionMapper.findTodayProblemIdByDifficulty(today, difficulty);

        // 없으면 새로 선택 (Pool 우선 → 기존 ALGO_PROBLEM fallback)
        if (problemId == null) {
            // 1. Pool에서 문제 가져오기 시도 (AI 생성 문제)
            // ALGO_CREATER = -1 → 시스템(데일리 미션)이 생성한 문제임을 표시
            problemId = problemPoolService.drawProblemForDailyMission(difficulty, -1L);

            // 2. Pool이 비어있으면 기존 ALGO_PROBLEM에서 랜덤 선택 (fallback)
            if (problemId == null) {
                log.info("Pool이 비어있어 기존 문제에서 선택 - difficulty: {}", difficulty);
                problemId = missionMapper.findRandomProblemIdByDifficulty(difficulty);
            }
        }

        // 미션 1: AI 문제 생성 미션
        DailyMissionDto generateMission = new DailyMissionDto();
        generateMission.setUserId(userId);
        generateMission.setMissionDate(today);
        generateMission.setMissionType(MissionType.PROBLEM_GENERATE);
        generateMission.setProblemId(null);  // 문제 생성 미션은 문제 ID 없음
        generateMission.setRewardPoints(rewardPoints);
        missionMapper.insertMission(generateMission);

        // 미션 2: 문제 풀기 미션
        DailyMissionDto solveMission = new DailyMissionDto();
        solveMission.setUserId(userId);
        solveMission.setMissionDate(today);
        solveMission.setMissionType(MissionType.PROBLEM_SOLVE);
        solveMission.setProblemId(problemId);
        solveMission.setRewardPoints(rewardPoints);
        missionMapper.insertMission(solveMission);

        log.info("사용자 {} 데일리 미션 생성 완료 (레벨: {}, 보상: {}P)", userId, level.getDisplayName(), rewardPoints);
    }

    /**
     * 미션 완료 처리
     * 미션이 없으면 자동 생성 후 완료 처리
     * - 0시 이후 가입한 신규 유저
     * - 데일리미션 페이지를 거치지 않고 직접 문제 생성하는 경우
     *
     * @param userId 사용자 ID
     * @param missionType 미션 타입
     * @param solvedProblemId 실제로 푼 문제 ID (PROBLEM_SOLVE일 때 필수, PROBLEM_GENERATE일 때 null)
     */
    @Transactional
    public MissionCompleteResult completeMission(Long userId, MissionType missionType, Long solvedProblemId) {
        LocalDate today = LocalDate.now();

        // 미션 조회
        DailyMissionDto mission = missionMapper.findMission(userId, today, missionType);

        // 미션이 없으면 자동 생성 (getTodayMissions와 동일한 로직)
        if (mission == null) {
            log.info("사용자 {} 오늘 미션이 없어서 자동 생성 - missionType: {}", userId, missionType);
            createDailyMissionsForUser(userId);
            mission = missionMapper.findMission(userId, today, missionType);

            if (mission == null) {
                log.error("미션 자동 생성 후에도 찾을 수 없음 - userId: {}, missionType: {}", userId, missionType);
                return MissionCompleteResult.notFound();
            }
        }

        // 이미 완료됨
        if (mission.isCompleted()) {
            return MissionCompleteResult.alreadyCompleted();
        }

        // ★ PROBLEM_SOLVE 미션: 데일리 미션에 할당된 문제와 실제 푼 문제가 일치하는지 검증
        if (missionType == MissionType.PROBLEM_SOLVE) {
            Long missionProblemId = mission.getProblemId();
            if (missionProblemId == null || !missionProblemId.equals(solvedProblemId)) {
                log.debug("데일리 미션 문제 불일치 - userId: {}, 미션문제: {}, 푼문제: {}",
                        userId, missionProblemId, solvedProblemId);
                return MissionCompleteResult.wrongProblem();
            }
        }

        // 미션 완료 처리
        missionMapper.completeMission(mission.getMissionId());

        // 보너스 포인트 지급 (XP는 AlgorithmJudgingService에서 일괄 처리)
        int rewardPoints = mission.getRewardPoints();
        String description = String.format("데일리 미션 완료: %s", missionType.getDescription());
        pointService.addRewardPoint(userId, rewardPoints, description);

        log.info("사용자 {} 미션 완료: {} (+{}P 보너스)", userId, missionType, rewardPoints);
        return MissionCompleteResult.success(rewardPoints);
    }

    /**
     * 사용자 레벨 조회 (없으면 생성)
     */
    @Transactional
    public UserAlgoLevelDto getOrCreateUserLevel(Long userId) {
        UserAlgoLevelDto level = missionMapper.findUserLevel(userId);
        if (level == null) {
            level = new UserAlgoLevelDto();
            level.setUserId(userId);
            level.setAlgoLevel(AlgoLevel.EMERALD);
            level.setTotalXp(0);
            level.setTotalSolved(0);
            level.setCurrentStreak(0);
            level.setMaxStreak(0);
            missionMapper.insertUserLevel(level);
            log.info("사용자 {} 알고리즘 레벨 생성: EMERALD (XP: 0)", userId);
        } else {
            // DB에서 로드 후 XP 기반으로 레벨 동기화
            level.syncLevelFromXp();
        }
        return level;
    }

    /**
     * 사용자 레벨 조회
     */
    @Transactional(readOnly = true)
    public UserAlgoLevelDto getUserLevel(Long userId) {
        return missionMapper.findUserLevel(userId);
    }

    /**
     * 오늘자의 문제 풀이 미션 문제 ID를 반환합니다. 없으면 null.
     * 미션이 생성되어 있지 않다면 생성 후 조회합니다.
     */
    @Transactional
    public Long getTodaySolveMissionProblemId(Long userId) {
        LocalDate today = LocalDate.now();
        List<DailyMissionDto> missions = missionMapper.findTodayMissions(userId, today);
        if (missions.isEmpty()) {
            createDailyMissionsForUser(userId);
            missions = missionMapper.findTodayMissions(userId, today);
        }
        DailyMissionDto solveMission = missionMapper.findMission(userId, today, MissionType.PROBLEM_SOLVE);
        return solveMission != null ? solveMission.getProblemId() : null;
    }

    /**
     * 사용자 통계 업데이트 (문제 풀이 완료 시) - XP 기반
     *
     * @param userId 사용자 ID
     * @param problemId 문제 ID
     * @param difficulty 문제 난이도
     * @return 획득한 XP (레벨업 시 음수 반환으로 표시하지 않고, XpRewardResult 반환)
     */
    @Transactional
    public XpRewardResult updateUserStatsWithXp(Long userId, Long problemId, ProblemDifficulty difficulty) {
        UserAlgoLevelDto level = getOrCreateUserLevel(userId);
        LocalDateTime lastSolved = level.getLastSolvedAt();
        LocalDate today = LocalDate.now();
        AlgoLevel previousLevel = level.getAlgoLevel();

        // 연속 풀이 계산
        int currentStreak = level.getCurrentStreak();
        if (lastSolved == null || lastSolved.toLocalDate().isBefore(today.minusDays(1))) {
            // 어제 풀지 않았으면 스트릭 초기화
            currentStreak = 1;
        } else if (lastSolved.toLocalDate().equals(today.minusDays(1))) {
            // 어제 풀었으면 스트릭 증가
            currentStreak++;
        }
        // 오늘 이미 풀었으면 유지

        // 최대 스트릭 업데이트
        int maxStreak = Math.max(level.getMaxStreak(), currentStreak);

        // 첫 정답 여부 확인 (ALGO_SUBMISSIONS 테이블 활용)
        boolean isFirstSolve = missionMapper.isFirstSolve(userId, problemId);

        // XP 계산 (난이도 + 첫 정답 보너스 + 스트릭 보너스)
        int earnedXp = difficulty.calculateXpWithBonus(currentStreak, isFirstSolve);

        // XP 추가 및 레벨 동기화
        int newTotalXp = level.getTotalXp() + earnedXp;
        AlgoLevel newLevel = AlgoLevel.fromXp(newTotalXp);
        boolean leveledUp = previousLevel != newLevel;

        // 통계 업데이트
        level.setTotalXp(newTotalXp);
        level.setTotalSolved(level.getTotalSolved() + 1);
        level.setCurrentStreak(currentStreak);
        level.setMaxStreak(maxStreak);
        level.setAlgoLevel(newLevel);
        level.setLastSolvedAt(LocalDateTime.now());

        missionMapper.updateUserLevel(level);

        if (leveledUp) {
            log.info("사용자 {} 레벨 업! {} -> {} (XP: {})", userId, previousLevel, newLevel, newTotalXp);
        }

        log.info("사용자 {} XP 획득: +{} (첫정답: {}, 스트릭: {}일, 총XP: {})",
                userId, earnedXp, isFirstSolve, currentStreak, newTotalXp);

        return new XpRewardResult(earnedXp, isFirstSolve, currentStreak, leveledUp, previousLevel, newLevel, newTotalXp);
    }

    /**
     * 기존 updateUserStats 호환용 (문제 ID/난이도 없이 호출 시)
     * @deprecated updateUserStatsWithXp 사용 권장
     */
    @Deprecated
    @Transactional
    public void updateUserStats(Long userId) {
        UserAlgoLevelDto level = getOrCreateUserLevel(userId);
        LocalDateTime lastSolved = level.getLastSolvedAt();
        LocalDate today = LocalDate.now();

        // 연속 풀이 계산
        int currentStreak = level.getCurrentStreak();
        if (lastSolved == null || lastSolved.toLocalDate().isBefore(today.minusDays(1))) {
            currentStreak = 1;
        } else if (lastSolved.toLocalDate().equals(today.minusDays(1))) {
            currentStreak++;
        }

        int maxStreak = Math.max(level.getMaxStreak(), currentStreak);
        int totalSolved = level.getTotalSolved() + 1;

        level.setTotalSolved(totalSolved);
        level.setCurrentStreak(currentStreak);
        level.setMaxStreak(maxStreak);
        level.setLastSolvedAt(LocalDateTime.now());

        missionMapper.updateUserLevel(level);
    }

    /**
     * 사용자의 구독 여부 확인
     * subscriptions 테이블에서 활성 구독(ACTIVE, 만료되지 않음) 여부 조회
     */
    @Transactional(readOnly = true)
    public boolean isSubscriber(Long userId) {
        List<Subscription> activeSubscriptions = subscriptionMapper.findActiveSubscriptionsByUserId(userId);
        boolean isSubscriber = activeSubscriptions != null && !activeSubscriptions.isEmpty();

        if (isSubscriber) {
            log.debug("사용자 {} 활성 구독 확인: {}", userId,
                    activeSubscriptions.get(0).getSubscriptionType());
        }

        return isSubscriber;
    }

    /**
     * 사용량 정보 조회
     */
    public UsageInfoResult getUsageInfo(Long userId) {
        boolean isSubscriber = isSubscriber(userId);
        RateLimitService.UsageInfo usage = rateLimitService.getUsage(userId);
        int remaining = rateLimitService.getRemainingUsage(userId, isSubscriber);

        return new UsageInfoResult(
                usage.generateCount(),
                usage.solveCount(),
                usage.getTotal(),
                remaining,
                isSubscriber
        );
    }

    /**
     * 모든 활성 사용자에 대해 데일리 미션 생성 (스케줄러용)
     */
    @Transactional
    public int createDailyMissionsForAllUsers() {
        List<Long> activeUserIds = missionMapper.findAllActiveUserIds();
        int created = 0;

        for (Long userId : activeUserIds) {
            try {
                createDailyMissionsForUser(userId);
                created++;
            } catch (Exception e) {
                log.error("사용자 {} 미션 생성 실패: {}", userId, e.getMessage());
            }
        }

        log.info("데일리 미션 생성 완료: {}명", created);
        return created;
    }

    /**
     * 미션 완료 결과
     */
    public record MissionCompleteResult(
            boolean success,
            String message,
            int rewardPoints,
            XpRewardResult xpResult
    ) {
        public static MissionCompleteResult success(int rewardPoints) {
            return new MissionCompleteResult(true, "미션 완료!", rewardPoints, null);
        }

        public static MissionCompleteResult success(int rewardPoints, XpRewardResult xpResult) {
            return new MissionCompleteResult(true, "미션 완료!", rewardPoints, xpResult);
        }

        public static MissionCompleteResult notFound() {
            return new MissionCompleteResult(false, "미션을 찾을 수 없습니다.", 0, null);
        }

        public static MissionCompleteResult alreadyCompleted() {
            return new MissionCompleteResult(false, "이미 완료된 미션입니다.", 0, null);
        }

        public static MissionCompleteResult wrongProblem() {
            return new MissionCompleteResult(false, "데일리 미션 문제가 아닙니다.", 0, null);
        }
    }

    /**
     * 사용량 정보 결과
     */
    public record UsageInfoResult(
            int generateCount,
            int solveCount,
            int totalUsage,
            int remaining,
            boolean isSubscriber
    ) {}

    /**
     * 사용자의 일별 정답 수 조회 (GitHub 잔디 캘린더용)
     * @param userId 사용자 ID
     * @param months 조회할 개월 수 (기본 12개월)
     * @return 날짜별 정답 수 리스트
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getDailySolveCounts(Long userId, int months) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(months);

        log.info("📊 잔디 캘린더 데이터 조회 - userId: {}, 기간: {} ~ {}", userId, startDate, endDate);

        return submissionMapper.selectDailySolveCountsByUserId(userId, startDate, endDate);
    }

    /**
     * XP 보상 결과
     */
    public record XpRewardResult(
            int earnedXp,
            boolean isFirstSolve,
            int currentStreak,
            boolean leveledUp,
            AlgoLevel previousLevel,
            AlgoLevel newLevel,
            int totalXp
    ) {
        /**
         * 보너스 상세 정보 문자열 생성
         */
        public String getBonusDescription() {
            StringBuilder sb = new StringBuilder();
            if (isFirstSolve) {
                sb.append("첫 정답 보너스 +50%");
            }
            if (currentStreak >= 3) {
                if (!sb.isEmpty()) sb.append(", ");
                int bonusPercent;
                if (currentStreak >= 30) {
                    bonusPercent = 50;
                } else if (currentStreak >= 14) {
                    bonusPercent = 30;
                } else if (currentStreak >= 7) {
                    bonusPercent = 20;
                } else {
                    bonusPercent = 10;
                }
                sb.append(String.format("스트릭 보너스 +%d%%", bonusPercent));
            }
            return sb.isEmpty() ? "기본 XP" : sb.toString();
        }
    }
}
