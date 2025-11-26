package kr.or.kosa.backend.freeboard.exception;

import kr.or.kosa.backend.commons.exception.custom.ErrorCode;

public enum FreeboardErrorCode implements ErrorCode {

    // 게시글 조회
    NOT_FOUND("FB001", "게시글을 찾을 수 없습니다."),
    DELETED_POST_ACCESS("FB002", "삭제된 게시글입니다."),

    // 게시글 권한
    NO_EDIT_PERMISSION("FB003", "게시글 수정 권한이 없습니다."),
    NO_DELETE_PERMISSION("FB004", "게시글 삭제 권한이 없습니다."),

    // 게시글 생성/수정/삭제
    INSERT_ERROR("FB005", "게시글 등록 중 오류가 발생했습니다."),
    UPDATE_ERROR("FB006", "게시글 수정 중 오류가 발생했습니다."),
    DELETE_ERROR("FB007", "게시글 삭제 중 오류가 발생했습니다."),

    // JSON 변환
    JSON_PARSE_ERROR("FB008", "게시글 콘텐츠 변환 중 오류가 발생했습니다.");

    private final String code;
    private final String message;

    FreeboardErrorCode(String code, String message) {
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