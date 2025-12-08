package kr.or.kosa.backend.codeboard.exception;

import kr.or.kosa.backend.commons.exception.custom.ErrorCode;

public enum CodeboardErrorCode implements ErrorCode {

    INSERT_ERROR("CB001", "게시글 등록 중 오류가 발생했습니다."),
    UPDATE_ERROR("CB002", "게시글 수정 중 오류가 발생했습니다."),
    DELETE_ERROR("CB003", "게시글 삭제 중 오류가 발생했습니다."),
    NOT_FOUND("CB004", "해당 게시글을 찾을 수 없습니다."),
    NO_EDIT_PERMISSION("CB005", "게시글 수정 권한이 없습니다."),
    NO_DELETE_PERMISSION("CB006", "게시글 삭제 권한이 없습니다."),

    JSON_PARSE_ERROR("CB007", "블록 내용을 JSON으로 변환할 수 없습니다."),
    INVALID_BLOCK_CONTENT("CB008", "허용되지 않는 블록 콘텐츠가 포함되어 있습니다.");

    private final String code;
    private final String message;

    CodeboardErrorCode(String code, String message) {
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