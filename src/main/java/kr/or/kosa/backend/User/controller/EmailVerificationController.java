package kr.or.kosa.backend.user.controller;

import kr.or.kosa.backend.user.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/email")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    // 인증 이메일 보내기
    @PostMapping("/send")
    public String sendEmail(@RequestParam String email) {
        emailVerificationService.sendVerificationEmail(email);
        return "인증 이메일을 보냈습니다.";
    }

    // 인증 코드 확인
    @PostMapping("/verify")
    public String verifyCode(
            @RequestParam String email,
            @RequestParam String code
    ) {
        boolean result = emailVerificationService.verifyCode(email, code);
        return result ? "인증 성공" : "인증 실패";
    }
}
