package kr.or.kosa.backend.commons.exception.custom;

import kr.or.kosa.backend.commons.exception.base.BaseSystemException;

public class CustomSystemException extends BaseSystemException {
    private final ErrorCode errorCode;

    public CustomSystemException(ErrorCode errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

}
