package kr.or.kosa.backend.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    EMAIL_DUPLICATE(400, "이미 사용 중인 이메일입니다."),
    NICKNAME_DUPLICATE(400, "이미 사용 중인 닉네임입니다."),

    EMAIL_NOT_VERIFIED(400, "이메일 인증이 완료되지 않았습니다."),
    INVALID_VERIFICATION_CODE(400, "인증 코드가 일치하지 않습니다."),

    INVALID_IMAGE_SIZE(400, "이미지 크기는 5MB 이하만 업로드할 수 있습니다."),
    INVALID_IMAGE_EXTENSION(400, "이미지 파일은 JPEG 또는 PNG 형식만 허용됩니다."),
    INVALID_IMAGE_FILE(400, "손상되었거나 올바르지 않은 이미지 파일입니다."),
    FILE_SAVE_ERROR(500, "파일 저장 중 오류가 발생했습니다."),

    USER_NOT_FOUND(404, "등록되지 않은 이메일입니다."),
    INVALID_PASSWORD(401, "비밀번호가 일치하지 않습니다."),

    BAD_REQUEST(400, "잘못된 요청입니다."),

    INVALID_TOKEN(401, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(401, "토큰이 만료되었습니다."),

    INVALID_OR_EXPIRED_TOKEN(401, "비밀번호 재설정 토큰이 유효하지 않거나 만료되었습니다."),

    INTERNAL_SERVER_ERROR(500, "서버 내부 오류가 발생했습니다.");

    private final int status;
    private final String message;

    ErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }
}