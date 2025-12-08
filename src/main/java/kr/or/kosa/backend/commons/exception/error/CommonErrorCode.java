package kr.or.kosa.backend.commons.exception.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode {

    INVALID_SORT_TYPE(HttpStatus.BAD_REQUEST, "정렬 타입은 필수입니다."),
    INVALID_SORT_DIRECTION(HttpStatus.BAD_REQUEST, "정렬 방향은 필수입니다."),
    INVALID_SORT_ENUM(HttpStatus.INTERNAL_SERVER_ERROR, "정렬 Enum은 반드시 getColumn() 메서드를 구현해야 합니다.");

    private final HttpStatus status;
    private final String message;
}
