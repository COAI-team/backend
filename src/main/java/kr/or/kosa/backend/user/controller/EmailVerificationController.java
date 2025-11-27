package kr.or.kosa.backend.user.controller;

import kr.or.kosa.backend.user.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/email")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    private static final String KEY_SUCCESS = "success";
    private static final String KEY_MESSAGE = "message";

    /**
     * 이메일 인증 코드 발송
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendEmail(@RequestParam String email) {

        long expireAt = emailVerificationService.sendVerificationEmail(email);

        boolean success = expireAt > 0;

        return ResponseEntity.ok(Map.of(
                KEY_SUCCESS, success,
                KEY_MESSAGE, success ?
                        "인증 이메일을 보냈습니다." :
                        "이메일 전송에 실패했습니다.",
                "expireAt", expireAt
        ));
    }

    /**
     * 인증 코드 검증
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyCode(
            @RequestParam String email,
            @RequestParam String code
    ) {
        boolean result = emailVerificationService.verifyCodeAndUpdate(email, code);

        return ResponseEntity.ok(Map.of(
                KEY_SUCCESS, result,
                KEY_MESSAGE, result ? "인증 성공" : "인증 실패"
        ));
    }
}