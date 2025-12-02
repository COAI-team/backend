package kr.or.kosa.backend.users.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private final StringRedisTemplate redisTemplate;
    private final EmailSender emailSender;

    private static final String VERIFY_CODE_PREFIX = "email:verify:";
    private static final String VERIFIED_PREFIX = "email:verified:";

    // 인증번호 존재시간 : 5분
    private static final long EXPIRATION_SECONDS = 5 * 60;

    /** 인증 코드 생성 + 이메일 전송 */
    @Override
    public long sendVerificationEmail(String email) {

        String code = UUID.randomUUID().toString().substring(0, 6);
        String codeKey = VERIFY_CODE_PREFIX + email;

        redisTemplate.opsForValue().set(
                codeKey,
                code,
                EXPIRATION_SECONDS,
                TimeUnit.SECONDS
        );

        String subject = "회원가입 이메일 인증코드";
        String text = "인증 코드: " + code + "\n5분 안에 입력해주세요.";

        boolean sent = send(email, subject, text);

        return sent ? System.currentTimeMillis() + (EXPIRATION_SECONDS * 1000) : -1;
    }

    /** 인증 코드 확인 + 인증 상태 저장 */
    @Override
    public boolean verifyCodeAndUpdate(String email, String requestCode) {

        // 이미 인증된 이메일이면 항상 성공 처리
        if (isVerified(email)) {
            return true;
        }

        String codeKey = VERIFY_CODE_PREFIX + email;
        String savedCode = redisTemplate.opsForValue().get(codeKey);

        if (savedCode == null) return false;
        if (!savedCode.equalsIgnoreCase(requestCode)) return false;

        redisTemplate.opsForValue().set(VERIFIED_PREFIX + email, "true", 1, TimeUnit.HOURS);
        redisTemplate.delete(codeKey);

        return true;
    }

    /** 이메일 인증 여부 */
    @Override
    public boolean isVerified(String email) {
        String key = VERIFIED_PREFIX + email;
        String verified = redisTemplate.opsForValue().get(key);

        return "true".equals(verified); // 올바른 로직
    }

    /** 이메일 전송 - void → boolean */
    @Override
    public boolean send(String to, String subject, String text) {
        boolean sent = emailSender.sendEmail(to, subject, text);
        if (!sent) {
            log.error("Email sending failed (returned false)");
        }
        return sent;
    }
}