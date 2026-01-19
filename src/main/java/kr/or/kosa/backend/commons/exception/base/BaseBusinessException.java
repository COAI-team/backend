package kr.or.kosa.backend.commons.exception.base;

import kr.or.kosa.backend.commons.exception.custom.ErrorCode;
import lombok.Getter;

@Getter
public abstract class BaseBusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    protected BaseBusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
