package kr.or.kosa.backend.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.UUID;

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
     * 토큰 즉시 삭제 (1회성)
     */
    public void deleteToken(String token) {
        redisTemplate.delete(RESET_TOKEN_PREFIX + token);
    }
}