package kr.or.kosa.backend.users.exception;

import kr.or.kosa.backend.commons.exception.custom.ErrorCode;

public enum UserErrorCode implements ErrorCode {

    EMAIL_DUPLICATE("USER001", "이미 사용 중인 이메일입니다."),
    EMAIL_SEND_FAIL("U503", "이메일 전송에 실패했습니다."),
    NICKNAME_DUPLICATE("USER002", "이미 사용 중인 닉네임입니다."),
    USER_NOT_FOUND("USER003", "등록되지 않은 이메일입니다."),
    INVALID_PASSWORD("USER004", "비밀번호가 일치하지 않습니다."),
    EMAIL_NOT_VERIFIED("USER005", "이메일 인증이 완료되지 않았습니다."),
    USER_CREATE_FAIL("USER501", "사용자 생성에 실패했습니다."),
    UPDATE_FAIL("USER502", "정보 업데이트에 실패했습니다."),
    FILE_SAVE_ERROR("USER012", "파일 저장 중 오류가 발생했습니다."),
    INVALID_TOKEN("USER014", "유효하지 않은 토큰입니다."),
    ALREADY_SCHEDULED_DELETE("USER100", "이미 탈퇴 예약 중인 계정입니다."),
    FILE_UPLOAD_FAILED("USER013", "파일 업로드에 실패했습니다."),
    ACCOUNT_PENDING_DELETE("USER101", "탈퇴 예약된 계정입니다. 복구 후 로그인 가능합니다.");

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