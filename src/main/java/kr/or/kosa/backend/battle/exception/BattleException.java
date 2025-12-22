package kr.or.kosa.backend.battle.exception;

import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;

public class BattleException extends CustomBusinessException {
    public BattleException(BattleErrorCode errorCode) {
        super(errorCode);
    }
}
