package kr.or.kosa.backend.codeBoard.exception;

import kr.or.kosa.backend.commons.exception.custom.ErrorCode;

public enum CodeBoardErrorCode implements ErrorCode {
    INSERT_ERROR("CB001", "게시글 등록 중 오류가 발생했습니다."),
    UPDATE_ERROR("CB002", "게시글 수정 중 오류가 발생했습니다."),
    DELETE_ERROR("CB003", "게시글 삭제 중 오류가 발생했습니다."),
    NOT_FOUND("CB004", "해당 게시글을 찾을 수 없습니다."),
    INVALID_INPUT("CB005", "요청 데이터가 유효하지 않습니다."),
    UNAUTHORIZED_ACCESS("CB006", "게시글에 접근할 권한이 없습니다."),
    DUPLICATE_TITLE("CB007", "이미 존재하는 제목입니다.");

    private final String code;
    private final String message;

    CodeBoardErrorCode(String code, String message) {
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
