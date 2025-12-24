package kr.or.kosa.backend.googleOTP.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminOtpDto {
    private Long adminOtpId;
    private Long userId;
    private String otpSecret;
    private boolean otpEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime enabledAt;
}
