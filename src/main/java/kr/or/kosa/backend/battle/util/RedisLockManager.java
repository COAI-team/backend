package kr.or.kosa.backend.battle.util;

import java.time.Duration;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RedisLockManager {

    private static final Duration DEFAULT_TTL = Duration.ofSeconds(5);
    private static final String UNLOCK_LUA = """
            if redis.call("get", KEYS[1]) == ARGV[1] then
                return redis.call("del", KEYS[1])
            else
                return 0
            end
            """;

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> unlockScript;

    public RedisLockManager(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.unlockScript = new DefaultRedisScript<>(UNLOCK_LUA, Long.class);
    }

    public String lock(String key) {
        return lock(key, DEFAULT_TTL);
    }

    public String lock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(key, token, ttl);
        return Boolean.TRUE.equals(locked) ? token : null;
    }

    public void unlock(String key, String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        stringRedisTemplate.execute(unlockScript, java.util.Collections.singletonList(key), token);
    }
}
