package kr.or.kosa.backend.battle.util;

import java.math.BigDecimal;

import kr.or.kosa.backend.battle.exception.BattleErrorCode;
import kr.or.kosa.backend.battle.exception.BattleException;
import org.springframework.stereotype.Component;

@Component
public class BattleValidator {
    private static final java.math.BigDecimal MAX_BET_AMOUNT = new java.math.BigDecimal("99999");
    private static final int MAX_TITLE_LENGTH = 50;
    private static final int MIN_DURATION_MINUTES = 1;
    private static final int MAX_DURATION_MINUTES = 120;

    public void validateBetAmount(BigDecimal betAmount) {
        if (betAmount == null || betAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BattleException(BattleErrorCode.BET_TOO_SMALL);
        }
        if (betAmount.compareTo(MAX_BET_AMOUNT) > 0) {
            throw new BattleException(BattleErrorCode.BET_TOO_LARGE);
        }
    }

    public void validateTitle(String title) {
        if (title != null && title.trim().length() > MAX_TITLE_LENGTH) {
            throw new BattleException(BattleErrorCode.TITLE_TOO_LONG);
        }
    }

    public void validateMaxDuration(Integer minutes) {
        if (minutes == null) return;
        if (minutes < MIN_DURATION_MINUTES || minutes > MAX_DURATION_MINUTES) {
            throw new BattleException(BattleErrorCode.DURATION_INVALID);
        }
    }
}
