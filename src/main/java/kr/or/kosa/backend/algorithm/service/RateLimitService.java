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
            return new UsageInfo(0, 0);
        }

        int generate = parseIntOrZero(entries.get("GENERATE"));
        int solve = parseIntOrZero(entries.get("SOLVE"));

        return new UsageInfo(generate, solve);
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
    public record UsageInfo(int generateCount, int solveCount) {
        public int getTotal() {
            return generateCount + solveCount;
        }

        public Map<String, Integer> toMap() {
            Map<String, Integer> map = new HashMap<>();
            map.put("generate", generateCount);
            map.put("solve", solveCount);
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
