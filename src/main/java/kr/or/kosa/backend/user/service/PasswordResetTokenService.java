package kr.or.kosa.backend.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetTokenService {

    private final StringRedisTemplate redisTemplate;

    private static final String RESET_TOKEN_PREFIX = "pwd:reset:";
    private static final long EXPIRE_MINUTES = 15;

    /**
     * 비밀번호 재설정 토큰 생성 & 저장
     */
    public String createResetToken(Integer userId) {
        String token = UUID.randomUUID().toString();

        redisTemplate.opsForValue().set(
                RESET_TOKEN_PREFIX + token,
                userId.toString(),
                EXPIRE_MINUTES,
                TimeUnit.MINUTES
        );

        return token;
    }

    /**
     * 토큰 검증 후 유저 ID 반환
     */
    public Integer validateToken(String token) {
        String value = redisTemplate.opsForValue().get(RESET_TOKEN_PREFIX + token);
        if (value == null) return null;
        return Integer.parseInt(value);
    }

    /**
     * 토큰 삭제 (boolean 반환)
     */
    public boolean deleteToken(String token) {
        try {
            Long deleted = redisTemplate.delete(Collections.singleton(RESET_TOKEN_PREFIX + token));
            return deleted > 0;
        } catch (Exception e) {
            log.error("Failed to delete reset token {}: {}", token, e.getMessage());
            return false;
        }
    }
}