package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.dto.enums.UsageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Rate Limiting 서비스
 * Redis 기반 일일 사용량 추적 및 제한
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final int FREE_USER_DAILY_LIMIT = 3;  // 무료 사용자 일일 한도
    private static final String KEY_PREFIX = "usage:daily:";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    // SSE 스트리밍 진행 중 상태 관리용 (재연결 중복 방지)
    private static final String IN_PROGRESS_PREFIX = "generation:inProgress:";
    private static final int IN_PROGRESS_TTL_SECONDS = 180;  // 3분 (LLM 생성 시간 고려)

    /**
     * 사용량 체크 및 증가 (체크 후 사용 가능하면 증가)
     *
     * @param userId       사용자 ID
     * @param type         사용 유형
     * @param isSubscriber 구독자 여부
     * @return 사용 가능 여부 및 잔여 횟수
     */
    public UsageCheckResult checkAndIncrementUsage(Long userId, UsageType type, boolean isSubscriber) {
        // 구독자는 무제한
        if (isSubscriber) {
            incrementUsage(userId, type);
            return UsageCheckResult.allowed(-1, -1);  // 무제한 표시
        }

        // 현재 사용량 조회
        UsageInfo currentUsage = getUsage(userId);
        int totalUsage = currentUsage.getTotal();

        // 한도 체크
        if (totalUsage >= FREE_USER_DAILY_LIMIT) {
            log.info("사용자 {} 일일 한도 초과: {}/{}", userId, totalUsage, FREE_USER_DAILY_LIMIT);
            return UsageCheckResult.denied(totalUsage, FREE_USER_DAILY_LIMIT);
        }

        // 사용량 증가
        incrementUsage(userId, type);
        int remaining = FREE_USER_DAILY_LIMIT - totalUsage - 1;

        log.info("사용자 {} 사용량 증가: {} (남은 횟수: {})", userId, type, remaining);
        return UsageCheckResult.allowed(totalUsage + 1, remaining);
    }

    /**
     * 현재 사용량만 조회 (증가 없이)
     */
    public UsageInfo getUsage(Long userId) {
        String key = buildKey(userId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

        if (entries.isEmpty()) {
            return new UsageInfo(0, 0, 0);
        }

        int generate = parseIntOrZero(entries.get("GENERATE"));
        int solve = parseIntOrZero(entries.get("SOLVE"));
        int analysis = parseIntOrZero(entries.get("ANALYSIS"));

        return new UsageInfo(generate, solve, analysis);
    }

    /**
     * 잔여 사용량 조회
     */
    public int getRemainingUsage(Long userId, boolean isSubscriber) {
        if (isSubscriber) {
            return -1;  // 무제한
        }

        UsageInfo usage = getUsage(userId);
        return Math.max(0, FREE_USER_DAILY_LIMIT - usage.getTotal());
    }

    // ===== SSE 스트리밍 전용 메서드 (재연결 중복 방지) =====

    /**
     * 사용량 체크만 수행 (증가 없음) - SSE 스트리밍용
     * <p>SSE 스트리밍에서는 요청 시작 시 체크만 하고, 성공 시에만 증가시킴
     *
     * @param userId       사용자 ID
     * @param type         사용 유형
     * @param isSubscriber 구독자 여부
     * @return 사용 가능 여부 및 잔여 횟수
     */
    public UsageCheckResult checkUsageOnly(Long userId, UsageType type, boolean isSubscriber) {
        if (isSubscriber) {
            return UsageCheckResult.allowed(-1, -1);  // 무제한
        }

        UsageInfo currentUsage = getUsage(userId);
        int totalUsage = currentUsage.getTotal();

        if (totalUsage >= FREE_USER_DAILY_LIMIT) {
            log.info("사용자 {} 일일 한도 초과 (체크): {}/{}", userId, totalUsage, FREE_USER_DAILY_LIMIT);
            return UsageCheckResult.denied(totalUsage, FREE_USER_DAILY_LIMIT);
        }

        int remaining = FREE_USER_DAILY_LIMIT - totalUsage;
        return UsageCheckResult.allowed(totalUsage, remaining);
    }

    /**
     * 사용량 증가만 수행 (체크 없음) - SSE 성공 시 호출
     * <p>SSE 스트리밍 요청이 성공적으로 완료된 후 호출
     *
     * @param userId 사용자 ID
     * @param type   사용 유형
     */
    public void incrementUsageOnly(Long userId, UsageType type) {
        incrementUsage(userId, type);
        log.info("사용자 {} 사용량 증가 (SSE 성공): {}", userId, type);
    }

    /**
     * 진행 중 상태 설정 - SSE 요청 시작 시 호출
     * <p>같은 사용자의 중복 요청(SSE 재연결)을 방지하기 위한 마커 설정
     *
     * @param userId 사용자 ID
     * @param type   사용 유형
     */
    public void setInProgress(Long userId, UsageType type) {
        String key = IN_PROGRESS_PREFIX + userId + ":" + type.name();
        redisTemplate.opsForValue().set(key, "1", IN_PROGRESS_TTL_SECONDS, TimeUnit.SECONDS);
        log.debug("진행 중 마커 설정 - userId: {}, type: {}, TTL: {}초", userId, type, IN_PROGRESS_TTL_SECONDS);
    }

    /**
     * 진행 중 상태 해제 - SSE 요청 완료/에러 시 호출
     *
     * @param userId 사용자 ID
     * @param type   사용 유형
     */
    public void clearInProgress(Long userId, UsageType type) {
        String key = IN_PROGRESS_PREFIX + userId + ":" + type.name();
        redisTemplate.delete(key);
        log.debug("진행 중 마커 해제 - userId: {}, type: {}", userId, type);
    }

    /**
     * 진행 중 여부 확인 - SSE 재연결 감지용
     *
     * @param userId 사용자 ID
     * @param type   사용 유형
     * @return 진행 중이면 true
     */
    public boolean isInProgress(Long userId, UsageType type) {
        String key = IN_PROGRESS_PREFIX + userId + ":" + type.name();
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 사용량 증가
     */
    private void incrementUsage(Long userId, UsageType type) {
        String key = buildKey(userId);

        // HINCRBY로 원자적 증가
        redisTemplate.opsForHash().increment(key, type.name(), 1);

        // TTL 설정 (자정까지)
        Long ttl = redisTemplate.getExpire(key);
        if (ttl == null || ttl == -1) {
            long secondsUntilMidnight = getSecondsUntilMidnight();
            redisTemplate.expire(key, secondsUntilMidnight, TimeUnit.SECONDS);
            log.debug("Redis 키 {} TTL 설정: {}초", key, secondsUntilMidnight);
        }
    }

    /**
     * Redis 키 생성
     */
    private String buildKey(Long userId) {
        String dateStr = LocalDate.now().format(DATE_FORMATTER);
        return KEY_PREFIX + userId + ":" + dateStr;
    }

    /**
     * 자정까지 남은 초 계산
     */
    private long getSecondsUntilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return Duration.between(now, midnight).getSeconds();
    }

    /**
     * Object를 int로 파싱
     */
    private int parseIntOrZero(Object value) {
        if (value == null) return 0;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 사용량 정보 DTO
     */
    public record UsageInfo(int generateCount, int solveCount, int analysisCount) {
        public int getTotal() {
            return generateCount + solveCount + analysisCount;
        }

        public Map<String, Integer> toMap() {
            Map<String, Integer> map = new HashMap<>();
            map.put("generate", generateCount);
            map.put("solve", solveCount);
            map.put("analysis", analysisCount);
            map.put("total", getTotal());
            return map;
        }
    }

    /**
     * 사용량 체크 결과 DTO
     */
    public record UsageCheckResult(
            boolean allowed,
            int currentUsage,
            int dailyLimit,
            int remaining,
            String message
    ) {
        public static UsageCheckResult allowed(int currentUsage, int remaining) {
            return new UsageCheckResult(true, currentUsage, FREE_USER_DAILY_LIMIT, remaining, null);
        }

        public static UsageCheckResult denied(int currentUsage, int dailyLimit) {
            return new UsageCheckResult(
                    false,
                    currentUsage,
                    dailyLimit,
                    0,
                    String.format("일일 무료 사용 한도(%d회)를 초과했습니다. 구독권을 구매하시면 무제한으로 이용 가능합니다.", dailyLimit)
            );
        }
    }
}
