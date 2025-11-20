package kr.or.kosa.backend.user.controller;

import kr.or.kosa.backend.user.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/email")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    // 인증 이메일 보내기
    @PostMapping("/send")
    public Map<String, Object> sendEmail(@RequestParam String email) {

        long expireAt = emailVerificationService.sendVerificationEmail(email);

        return Map.of(
                "message", "인증 이메일을 보냈습니다.",
                "expireAt", expireAt
        );
    }

    // 인증 코드 확인
    @PostMapping("/verify")
    public String verifyCode(
            @RequestParam String email,
            @RequestParam String code
    ) {
        boolean result = emailVerificationService.verifyCodeAndUpdate(email, code);

        return result ? "인증 성공" : "인증 실패";
    }
}
