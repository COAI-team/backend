package kr.or.kosa.backend.users.controller;

import kr.or.kosa.backend.users.service.EmailVerificationService;
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
        try {
            long expireAt = emailVerificationService.sendVerificationEmail(email);
            return ResponseEntity.ok(Map.of(
                    KEY_SUCCESS, true,
                    KEY_MESSAGE, "인증 이메일을 보냈습니다.",
                    "expireAt", expireAt
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    KEY_SUCCESS, false,
                    KEY_MESSAGE, "이메일 전송에 실패했습니다. 사유: " + e.getMessage()
            ));
        }
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