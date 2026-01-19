package kr.or.kosa.backend.commons.exception.base;

import kr.or.kosa.backend.commons.exception.custom.ErrorCode;
import lombok.Getter;

@Getter
public abstract class BaseSystemException extends RuntimeException {
    private final ErrorCode errorCode;

    protected BaseSystemException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

}
