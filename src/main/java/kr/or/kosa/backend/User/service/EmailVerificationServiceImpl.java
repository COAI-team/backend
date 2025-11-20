package kr.or.kosa.backend.user.service;

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

    // 인증번호 존재시간 : 5분
    private static final long EXPIRATION_SECONDS = 5 * 60;

    @Override
    public long sendVerificationEmail(String email) {

        String code = UUID.randomUUID().toString().substring(0, 6);

        // 인증 코드 key
        String codeKey = "email:verify:" + email;

        // 인증번호 저장 + TTL 5분
        redisTemplate.opsForValue().set(codeKey, code, EXPIRATION_SECONDS, TimeUnit.SECONDS);

        // 이메일 전송
        String subject = "회원가입 이메일 인증코드";
        String text = "인증 코드: " + code + "\n5분 안에 입력해주세요.";

        emailSender.sendEmail(email, subject, text);

        // 프론트가 만료까지 카운트다운 할 수 있게 expireAt 제공
        return System.currentTimeMillis() + (EXPIRATION_SECONDS * 1000);
    }

    @Override
    public boolean verifyCodeAndUpdate(String email, String requestCode) {

        String codeKey = "email:verify:" + email;
        String savedCode = redisTemplate.opsForValue().get(codeKey);

        if (savedCode == null) {
            return false;
        }

        if (!savedCode.equals(requestCode)) {
            return false;
        }

        // 인증 성공 → 인증 완료 상태 저장
        String verifiedKey = "email:verified:" + email;
        redisTemplate.opsForValue().set(verifiedKey, "true", 1, TimeUnit.HOURS);

        // 인증코드 제거 (이제 필요 없음)
        redisTemplate.delete(codeKey);

        return true;
    }

    @Override
    public boolean isVerified(String email) {
        String key = "email:verified:" + email;
        String verified = redisTemplate.opsForValue().get(key);

        return verified != null && verified.equals("true");
    }

    @Override
    public void send(String to, String subject, String text) {
        emailSender.sendEmail(to, subject, text);
    }
}