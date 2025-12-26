package kr.or.kosa.backend.googleOTP.service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import kr.or.kosa.backend.googleOTP.dto.AdminOtpDto;
import kr.or.kosa.backend.googleOTP.exception.GoogleOTPErrorCode;
import kr.or.kosa.backend.googleOTP.mapper.AdminOtpMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Slf4j
public class AdminOtpService {

    private final AdminOtpMapper adminOtpMapper;

    // ⭐ Google Authenticator 설정 (절대 분산시키지 말 것)
    private final GoogleAuthenticator gAuth =
        new GoogleAuthenticator(
            new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
                .setWindowSize(3) // ±30~60초 허용
                .build()
        );

    /** OTP 시크릿 생성 or 재발급 */
    @Transactional
    public String generateOtp(Long userId) {

        AdminOtpDto existing = adminOtpMapper.findByUserId(userId);

        // ✅ 이미 활성화된 OTP는 재발급 금지 (보안 핵심)
        if (existing != null && existing.isOtpEnabled()) {
            throw new CustomBusinessException(
                GoogleOTPErrorCode.OTP_ALREADY_ENABLED
            );
        }

        String secret;
        try {
            secret = gAuth.createCredentials().getKey();
        } catch (Exception e) {
            throw new CustomBusinessException(
                GoogleOTPErrorCode.OTP_GENERATION_FAILED
            );
        }

        adminOtpMapper.upsert(
            AdminOtpDto.builder()
                .userId(userId)
                .otpSecret(secret)
                .build()
        );

        return secret;
    }

    /** OTP 검증 + 활성화 */
    @Transactional
    public boolean verifyAndEnable(Long userId, int code) {

        AdminOtpDto otp = adminOtpMapper.findByUserId(userId);

        if (otp == null) {
            throw new CustomBusinessException(
                GoogleOTPErrorCode.OTP_NOT_REGISTERED
            );
        }

        if (otp.isOtpEnabled()) {
            throw new CustomBusinessException(
                GoogleOTPErrorCode.OTP_ALREADY_ENABLED
            );
        }

        // 디버깅 로그 (개발 중만 유지)
        int serverCode = gAuth.getTotpPassword(otp.getOtpSecret());
        log.info("📟 서버 OTP = {}", serverCode);
        log.info("👤 사용자 OTP = {}", code);

        boolean valid = gAuth.authorize(otp.getOtpSecret(), code);

        if (!valid) {
            throw new CustomBusinessException(
                GoogleOTPErrorCode.OTP_INVALID_CODE
            );
        }
        adminOtpMapper.enableOtp(userId);
        return true;
    }

    /** 관리자 OTP 활성 여부 */
    public boolean isOtpEnabled(Long userId) {
        AdminOtpDto otp = adminOtpMapper.findByUserId(userId);
        return otp != null && otp.isOtpEnabled();
    }


    @Transactional
    public String resetOtp(Long userId) {

        AdminOtpDto existing = adminOtpMapper.findByUserId(userId);

        if (existing == null) {
            throw new CustomBusinessException(
                GoogleOTPErrorCode.OTP_NOT_REGISTERED
            );
        }

        // 기존 OTP 비활성화
        adminOtpMapper.disableOtp(userId);

        String secret;
        try {
            secret = gAuth.createCredentials().getKey();
        } catch (Exception e) {
            throw new CustomBusinessException(
                GoogleOTPErrorCode.OTP_GENERATION_FAILED
            );
        }

        adminOtpMapper.upsert(
            AdminOtpDto.builder()
                .userId(userId)
                .otpSecret(secret)
                .otpEnabled(false)
                .build()
        );

        return secret;
    }


}

