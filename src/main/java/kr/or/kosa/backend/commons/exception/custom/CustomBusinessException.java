package kr.or.kosa.backend.commons.exception.custom;

import kr.or.kosa.backend.commons.exception.base.BaseBusinessException;
import lombok.Getter;

@Getter
public class CustomBusinessException extends BaseBusinessException {
    private final ErrorCode errorCode;

    public CustomBusinessException(ErrorCode errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }
}
