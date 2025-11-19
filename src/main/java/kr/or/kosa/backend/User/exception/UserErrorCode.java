package kr.or.kosa.backend.user.exception;

import kr.or.kosa.backend.commons.exception.custom.ErrorCode;

public enum UserErrorCode implements ErrorCode {

    EMAIL_DUPLICATE("USER001", "이미 사용 중인 이메일입니다."),
    NICKNAME_DUPLICATE("USER002", "이미 사용 중인 닉네임입니다."),
    USER_NOT_FOUND("USER003", "등록되지 않은 이메일입니다."),
    INVALID_PASSWORD("USER004", "비밀번호가 일치하지 않습니다."),
    EMAIL_NOT_VERIFIED("USER005", "이메일 인증이 완료되지 않았습니다."),
    INVALID_VERIFICATION_CODE("USER006", "인증 코드가 일치하지 않습니다."),
    INVALID_INPUT("USER007", "입력값이 유효하지 않습니다."),
    UNAUTHORIZED("USER008", "권한이 없습니다."),

    INVALID_IMAGE_SIZE("USER009", "이미지 크기는 5MB 이하만 업로드할 수 있습니다."),
    INVALID_IMAGE_EXTENSION("USER010", "이미지 파일은 JPEG 또는 PNG 형식만 허용됩니다."),
    INVALID_IMAGE_FILE("USER011", "손상되었거나 올바르지 않은 이미지 파일입니다."),
    FILE_SAVE_ERROR("USER012", "파일 저장 중 오류가 발생했습니다."),

    BAD_REQUEST("USER013", "잘못된 요청입니다."),

    INVALID_TOKEN("USER014", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN("USER015", "토큰이 만료되었습니다."),
    INVALID_OR_EXPIRED_TOKEN("USER016", "비밀번호 재설정 토큰이 유효하지 않거나 만료되었습니다."),

    INTERNAL_SERVER_ERROR("USER017", "서버 내부 오류가 발생했습니다.");

    private final String code;
    private final String message;

    UserErrorCode(String code, String message) {
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