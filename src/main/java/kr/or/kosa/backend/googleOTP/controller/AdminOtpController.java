package kr.or.kosa.backend.googleOTP.controller;

import kr.or.kosa.backend.commons.response.ApiResponse;
import kr.or.kosa.backend.googleOTP.service.AdminOtpService;
import kr.or.kosa.backend.security.jwt.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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

    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reset(
        @AuthenticationPrincipal JwtUserDetails user
    ) {

        Long userId = user.id();

        String secret = adminOtpService.resetOtp(userId);
        String otpAuthUrl =
            "otpauth://totp/CodeAi:" +
                URLEncoder.encode(user.email(), StandardCharsets.UTF_8) +
                "?secret=" + secret +
                "&issuer=CodeAi";


        Map<String, Object> result = new HashMap<>();
        result.put("secret", secret);
        result.put("otpAuthUrl", otpAuthUrl
        );

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Boolean>> status(
        @AuthenticationPrincipal JwtUserDetails user
    ) {
        boolean enabled = adminOtpService.isOtpEnabled(user.id());
        return ApiResponse.success(Map.of("enabled", enabled));
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
