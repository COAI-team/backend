package kr.or.kosa.backend.googleOTP.exception;

import kr.or.kosa.backend.commons.exception.custom.ErrorCode;

public enum GoogleOTPErrorCode implements ErrorCode {

    // ====== OTP 생성 / 등록 ======
    OTP_ALREADY_ENABLED(
        "OTP_001",
        "이미 OTP가 활성화된 계정입니다."
    ),

    OTP_GENERATION_FAILED(
        "OTP_002",
        "OTP 시크릿 생성에 실패했습니다."
    ),

    // ====== OTP 검증 ======
    OTP_NOT_REGISTERED(
        "OTP_003",
        "OTP가 등록되지 않은 관리자 계정입니다."
    ),

    OTP_INVALID_CODE(
        "OTP_004",
        "OTP 인증 번호가 올바르지 않습니다."
    ),

    OTP_EXPIRED_OR_OUT_OF_WINDOW(
        "OTP_005",
        "OTP 인증 시간이 만료되었거나 유효하지 않습니다."
    ),

    // ====== 보안 / 상태 ======
    OTP_DISABLED(
        "OTP_006",
        "OTP가 비활성화된 상태입니다."
    );

    private final String code;
    private final String message;

    GoogleOTPErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
