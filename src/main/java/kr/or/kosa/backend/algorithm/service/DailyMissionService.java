package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.dto.DailyMissionDto;
import kr.or.kosa.backend.algorithm.dto.UserAlgoLevelDto;
import kr.or.kosa.backend.algorithm.dto.enums.AlgoLevel;
import kr.or.kosa.backend.algorithm.dto.enums.MissionType;
import kr.or.kosa.backend.algorithm.mapper.DailyMissionMapper;
import kr.or.kosa.backend.pay.entity.Subscription;
import kr.or.kosa.backend.pay.repository.SubscriptionMapper;
import kr.or.kosa.backend.pay.service.PointService;
import kr.or.kosa.backend.users.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 데일리 미션 서비스
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DailyMissionService {

    private final DailyMissionMapper missionMapper;
    private final UserMapper userMapper;
    private final PointService pointService;
    private final RateLimitService rateLimitService;
    private final SubscriptionMapper subscriptionMapper;

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

        // 없으면 새로 랜덤 선택
        if (problemId == null) {
            problemId = missionMapper.findRandomProblemIdByDifficulty(difficulty);
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
     */
    @Transactional
    public MissionCompleteResult completeMission(Long userId, MissionType missionType) {
        LocalDate today = LocalDate.now();

        // 미션 조회
        DailyMissionDto mission = missionMapper.findMission(userId, today, missionType);
        if (mission == null) {
            return MissionCompleteResult.notFound();
        }

        // 이미 완료됨
        if (mission.isCompleted()) {
            return MissionCompleteResult.alreadyCompleted();
        }

        // 미션 완료 처리
        missionMapper.completeMission(mission.getMissionId());

        // 포인트 지급
        int rewardPoints = mission.getRewardPoints();
        String description = String.format("데일리 미션 완료: %s", missionType.getDescription());
        pointService.addRewardPoint(userId, rewardPoints, description);

        // 사용자 레벨 통계 업데이트 (문제 풀기 미션만)
        if (missionType == MissionType.PROBLEM_SOLVE) {
            updateUserStats(userId);
        }

        log.info("사용자 {} 미션 완료: {} (+{}P)", userId, missionType, rewardPoints);
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
            level.setTotalSolved(0);
            level.setCurrentStreak(0);
            level.setMaxStreak(0);
            missionMapper.insertUserLevel(level);
            log.info("사용자 {} 알고리즘 레벨 생성: EMERALD", userId);
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
     * 사용자 통계 업데이트 (문제 풀이 완료 시)
     */
    @Transactional
    public void updateUserStats(Long userId) {
        UserAlgoLevelDto level = getOrCreateUserLevel(userId);
        LocalDateTime lastSolved = level.getLastSolvedAt();
        LocalDate today = LocalDate.now();

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

        // 레벨 업 체크
        int totalSolved = level.getTotalSolved() + 1;
        AlgoLevel newLevel = calculateLevel(totalSolved);

        level.setTotalSolved(totalSolved);
        level.setCurrentStreak(currentStreak);
        level.setMaxStreak(maxStreak);
        level.setAlgoLevel(newLevel);
        level.setLastSolvedAt(LocalDateTime.now());

        missionMapper.updateUserLevel(level);

        if (newLevel != level.getAlgoLevel()) {
            log.info("사용자 {} 레벨 업: {} -> {}", userId, level.getAlgoLevel(), newLevel);
        }
    }

    /**
     * 총 풀이 수에 따른 레벨 계산
     */
    private AlgoLevel calculateLevel(int totalSolved) {
        if (totalSolved >= 100) {
            return AlgoLevel.DIAMOND;
        } else if (totalSolved >= 50) {
            return AlgoLevel.RUBY;
        } else if (totalSolved >= 20) {
            return AlgoLevel.SAPPHIRE;
        } else {
            return AlgoLevel.EMERALD;
        }
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
            int rewardPoints
    ) {
        public static MissionCompleteResult success(int rewardPoints) {
            return new MissionCompleteResult(true, "미션 완료!", rewardPoints);
        }

        public static MissionCompleteResult notFound() {
            return new MissionCompleteResult(false, "미션을 찾을 수 없습니다.", 0);
        }

        public static MissionCompleteResult alreadyCompleted() {
            return new MissionCompleteResult(false, "이미 완료된 미션입니다.", 0);
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
}
