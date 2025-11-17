package kr.or.kosa.backend.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationServiceImpl implements EmailVerificationService {

    // 이메일별 인증 코드 저장
    private final Map<String, String> verificationCodes = new ConcurrentHashMap<>();

    // 인증 완료 여부 저장
    private final Map<String, Boolean> verifiedEmails = new ConcurrentHashMap<>();

    private final EmailSender emailSender;

    @Override
    public void sendVerificationEmail(String email) {

        String code = UUID.randomUUID().toString().substring(0, 6);

        verificationCodes.put(email, code);
        verifiedEmails.put(email, false);

        String subject = "회원가입 이메일 인증코드";
        String text = "인증 코드: " + code;

        emailSender.sendEmail(email, subject, text);
    }

    @Override
    public boolean verifyCode(String email, String requestCode) {
        String savedCode = verificationCodes.get(email);

        if (savedCode != null && savedCode.equals(requestCode)) {
            verifiedEmails.put(email, true);
            verificationCodes.remove(email);
            return true;
        }

        return false;
    }

    @Override
    public boolean isVerified(String email) {
        return verifiedEmails.getOrDefault(email, false);
    }
}