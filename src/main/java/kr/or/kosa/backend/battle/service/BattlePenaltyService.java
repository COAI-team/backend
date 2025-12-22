package kr.or.kosa.backend.battle.service;

import java.time.Duration;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BattlePenaltyService {

    private static final String KEY_LAST5 = "battle:dc:last5:";
    private static final String KEY_STREAK = "battle:dc:streak:";
    private static final String KEY_PENALTY = "battle:penalty:";
    private static final Duration PENALTY_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate stringRedisTemplate;

    public boolean hasPenalty(Long userId) {
        if (userId == null) return false;
        Boolean exists = stringRedisTemplate.hasKey(KEY_PENALTY + userId);
        return Boolean.TRUE.equals(exists);
    }

    public long penaltyTtlSeconds(Long userId) {
        if (userId == null) return 0;
        Long ttl = stringRedisTemplate.getExpire(KEY_PENALTY + userId);
        return ttl != null ? ttl : 0;
    }

    public void recordDisconnectLoss(Long userId) {
        if (userId == null) return;
        String last5Key = KEY_LAST5 + userId;
        String streakKey = KEY_STREAK + userId;

        stringRedisTemplate.opsForList().leftPush(last5Key, "1");
        stringRedisTemplate.opsForList().trim(last5Key, 0, 4);
        Long streak = stringRedisTemplate.opsForValue().increment(streakKey);
        if (streak != null && streak > 1000) {
            stringRedisTemplate.opsForValue().set(streakKey, "1");
            streak = 1L;
        }

        List<String> last5 = stringRedisTemplate.opsForList().range(last5Key, 0, 4);
        long disconnectCount = last5 == null ? 0 : last5.stream().filter("1"::equals).count();

        if (disconnectCount >= 3 || (streak != null && streak >= 3)) {
            stringRedisTemplate.opsForValue().set(KEY_PENALTY + userId, "1", PENALTY_TTL);
        }
    }

    public void recordNormalFinish(Long userId) {
        if (userId == null) return;
        String last5Key = KEY_LAST5 + userId;
        String streakKey = KEY_STREAK + userId;

        stringRedisTemplate.opsForList().leftPush(last5Key, "0");
        stringRedisTemplate.opsForList().trim(last5Key, 0, 4);
        stringRedisTemplate.opsForValue().set(streakKey, "0");
    }
}
