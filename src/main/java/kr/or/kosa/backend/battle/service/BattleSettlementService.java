package kr.or.kosa.backend.battle.service;

import java.math.BigDecimal;
import java.util.Objects;

import kr.or.kosa.backend.battle.domain.BattleRoomState;
import kr.or.kosa.backend.battle.domain.BattleSettlementStatus;
import kr.or.kosa.backend.battle.port.BattlePointPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BattleSettlementService {

    private final BattlePointPort battlePointPort;
    private final BattleMatchService battleMatchService;

    @Transactional
    public void holdForCountdown(BattleRoomState state) {
        BigDecimal betAmount = state.getBetAmount();
        if (betAmount == null || betAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        battlePointPort.holdBet(state.getMatchId(), state.getHostUserId(), betAmount);
        if (state.getGuestUserId() != null) {
            battlePointPort.holdBet(state.getMatchId(), state.getGuestUserId(), betAmount);
        }
        battleMatchService.updateSettlementStatus(state.getMatchId(), BattleSettlementStatus.HELD);
        log.info("[battle] matchId={} action=hold bet={}", state.getMatchId(), betAmount);
    }

    @Transactional
    public void settle(String matchId, Long winnerUserId, Long loserUserId, BigDecimal betAmount) {
        if (Objects.isNull(winnerUserId) || Objects.isNull(loserUserId)) {
            return;
        }
        if (betAmount == null || betAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        boolean settled = battlePointPort.settleWin(matchId, winnerUserId, loserUserId, betAmount);
        if (settled) {
            battleMatchService.updateSettlementStatus(matchId, BattleSettlementStatus.SETTLED);
        }
    }

    @Transactional
    public void refundAll(BattleRoomState state) {
        BigDecimal betAmount = state.getBetAmount();
        if (betAmount == null || betAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        boolean refunded = false;
        if (state.getHostUserId() != null) {
            refunded |= battlePointPort.refund(state.getMatchId(), state.getHostUserId(), betAmount);
        }
        if (state.getGuestUserId() != null) {
            refunded |= battlePointPort.refund(state.getMatchId(), state.getGuestUserId(), betAmount);
        }
        if (refunded) {
            battleMatchService.updateSettlementStatus(state.getMatchId(), BattleSettlementStatus.REFUNDED);
            log.info("[battle] matchId={} action=refund bet={}", state.getMatchId(), betAmount);
        }
    }
}
