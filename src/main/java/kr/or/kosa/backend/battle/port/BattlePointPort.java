package kr.or.kosa.backend.battle.port;

import java.math.BigDecimal;

public interface BattlePointPort {
    void holdBet(String matchId, Long userId, BigDecimal amount);

    boolean settleWin(String matchId, Long winnerUserId, Long loserUserId, BigDecimal amount);

    boolean refund(String matchId, Long userId, BigDecimal amount);

    BigDecimal getBalance(Long userId);
}
