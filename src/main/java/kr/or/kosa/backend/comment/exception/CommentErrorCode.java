package kr.or.kosa.backend.comment.exception;


import kr.or.kosa.backend.commons.exception.custom.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommentErrorCode implements ErrorCode {

    NOT_FOUND(HttpStatus.BAD_REQUEST, "C001", "댓글을 찾을 수 없습니다"),
    PARENT_NOT_FOUND(HttpStatus.BAD_REQUEST, "C002", "부모 댓글을 찾을 수 없습니다"),
    DEPTH_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "C003", "대댓글에는 답글을 달 수 없습니다"),
    NO_EDIT_PERMISSION(HttpStatus.BAD_REQUEST, "C004", "본인의 댓글만 수정할 수 있습니다"),
    NO_DELETE_PERMISSION(HttpStatus.BAD_REQUEST, "C005", "본인의 댓글만 삭제할 수 있습니다"),
    ALREADY_DELETED(HttpStatus.BAD_REQUEST, "C006", "삭제된 댓글은 수정할 수 없습니다"),
    BOARD_NOT_FOUND(HttpStatus.BAD_REQUEST, "C007", "게시글을 찾을 수 없습니다"),
    INVALID_BOARD_TYPE(HttpStatus.BAD_REQUEST, "C008", "지원하지 않는 게시판 타입입니다"),
    INSERT_ERROR(HttpStatus.BAD_REQUEST, "C009", "댓글 작성에 실패했습니다"),
    UPDATE_ERROR(HttpStatus.BAD_REQUEST, "C010", "댓글 수정에 실패했습니다"),
    DELETE_ERROR(HttpStatus.BAD_REQUEST, "C011", "댓글 삭제에 실패했습니다");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
