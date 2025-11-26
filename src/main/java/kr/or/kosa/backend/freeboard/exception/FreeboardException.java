package kr.or.kosa.backend.freeboard.exception;

import kr.or.kosa.backend.commons.exception.base.BaseBusinessException;

public class FreeboardException extends BaseBusinessException {

    public FreeboardException(FreeboardErrorCode errorCode) {
        super(errorCode);
    }
}
