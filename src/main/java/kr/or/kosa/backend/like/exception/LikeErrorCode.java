package kr.or.kosa.backend.like.exception;

import kr.or.kosa.backend.commons.exception.custom.ErrorCode;

public enum LikeErrorCode implements ErrorCode {

    // 공통
    INVALID_REFERENCE("LK001", "유효하지 않은 참조 대상입니다."),
    UNAUTHORIZED("LK002", "로그인이 필요합니다."),

    // 좋아요 처리
    ALREADY_LIKED("LK003", "이미 좋아요한 대상입니다."),
    NOT_LIKED("LK004", "좋아요 상태가 아닙니다."),
    INSERT_ERROR("LK005", "좋아요 등록 중 오류가 발생했습니다."),
    DELETE_ERROR("LK006", "좋아요 취소 중 오류가 발생했습니다."),

    // 연속 요청
    TOO_MANY_REQUESTS("LK007", "좋아요 요청이 너무 빠릅니다. 잠시 후 다시 시도해주세요.");

    private final String code;
    private final String message;

    LikeErrorCode(String code, String message) {
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
