package kr.or.kosa.backend.commons.exception.custom;

import kr.or.kosa.backend.commons.exception.base.BaseSystemException;
import lombok.Getter;

@Getter
public class CustomSystemException extends BaseSystemException {
    private final ErrorCode errorCode;

    public CustomSystemException(ErrorCode errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }
}
