package kr.or.kosa.backend.users.controller;

import kr.or.kosa.backend.users.dto.EmailResponse;  // ✅ import
import kr.or.kosa.backend.users.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/email")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    private static final EmailResponse VERIFY_SUCCESS =
            EmailResponse.builder()
                    .success(true)
                    .message("인증 성공")
                    .build();

    private static final EmailResponse VERIFY_FAILURE =
            EmailResponse.builder()
                    .success(false)
                    .message("인증 실패")
                    .build();

    /**
     * 이메일 인증 코드 발송
     */
    @PostMapping("/send")
    public ResponseEntity<EmailResponse> sendEmail(@RequestParam String email) {
        try {
            long expireAt = emailVerificationService.sendVerificationEmail(email);
            System.out.println("expireAt = " + expireAt);
            return ResponseEntity.ok(EmailResponse.builder()
                    .success(true)
                    .message("인증 이메일을 보냈습니다.")
                    .expireAt(expireAt)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(EmailResponse.builder()
                            .success(false)
                            .message("이메일 전송에 실패했습니다. 사유: " + e.getMessage())
                            .build());
        }
    }

    /**
     * 인증 코드 검증
     */
    @PostMapping("/verify")
    public ResponseEntity<EmailResponse> verifyCode(
            @RequestParam String email,
            @RequestParam String code
    ) {
        boolean result = emailVerificationService.verifyCodeAndUpdate(email, code);
        return ResponseEntity.ok(result ? VERIFY_SUCCESS : VERIFY_FAILURE);
    }
}