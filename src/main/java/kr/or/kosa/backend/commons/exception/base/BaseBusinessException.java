package kr.or.kosa.backend.commons.exception.base;

import kr.or.kosa.backend.commons.exception.custom.ErrorCode;

public abstract class BaseBusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BaseBusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
    public ErrorCode getErrorCode() {
        return errorCode;
    }

}
