package kr.or.kosa.backend.googleOTP.controller;

import kr.or.kosa.backend.commons.response.ApiResponse;
import kr.or.kosa.backend.googleOTP.service.AdminOtpService;
import kr.or.kosa.backend.security.jwt.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/otp")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminOtpController {

    private final AdminOtpService adminOtpService;

    /** OTP 생성 */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<Map<String, String>>> generate(
        @AuthenticationPrincipal JwtUserDetails user
    ) {
        String secret = adminOtpService.generateOtp(user.id());

        String otpAuthUrl =
            "otpauth://totp/CodeAi:" + user.email()
                + "?secret=" + secret
                + "&issuer=CodeAi";

        return ResponseEntity.ok(ApiResponse.success(Map.of(
            "secret", secret,
            "otpAuthUrl", otpAuthUrl)));

    }

    /** OTP 검증 */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verify(
        @RequestParam int code,
        @AuthenticationPrincipal JwtUserDetails user
    ) {
        boolean success =
            adminOtpService.verifyAndEnable(user.id(), code);


        return ResponseEntity.ok(ApiResponse.success(Map.of("success", success)));
    }
}
