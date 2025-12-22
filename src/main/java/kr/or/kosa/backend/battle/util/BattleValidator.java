package kr.or.kosa.backend.battle.util;

import java.math.BigDecimal;

import kr.or.kosa.backend.battle.exception.BattleErrorCode;
import kr.or.kosa.backend.battle.exception.BattleException;
import org.springframework.stereotype.Component;

@Component
public class BattleValidator {

    public void validateBetAmount(BigDecimal betAmount) {
        if (betAmount == null || betAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BattleException(BattleErrorCode.BET_TOO_SMALL);
        }
    }
}
