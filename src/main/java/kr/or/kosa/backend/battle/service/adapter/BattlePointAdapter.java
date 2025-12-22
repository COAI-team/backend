package kr.or.kosa.backend.battle.service.adapter;

import java.math.BigDecimal;
import java.util.Objects;

import kr.or.kosa.backend.battle.domain.BattleHoldStatus;
import kr.or.kosa.backend.battle.domain.BattlePointHold;
import kr.or.kosa.backend.battle.exception.BattleErrorCode;
import kr.or.kosa.backend.battle.exception.BattleException;
import kr.or.kosa.backend.battle.mapper.BattlePointHistoryMapper;
import kr.or.kosa.backend.battle.mapper.BattlePointHoldMapper;
import kr.or.kosa.backend.battle.port.BattlePointPort;
import kr.or.kosa.backend.pay.entity.UserPoint;
import kr.or.kosa.backend.pay.repository.PointMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@Transactional
public class BattlePointAdapter implements BattlePointPort {

    private final PointMapper pointMapper;
    private final BattlePointHoldMapper battlePointHoldMapper;
    private final BattlePointHistoryMapper battlePointHistoryMapper;

    @Override
    public void holdBet(String matchId, Long userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BattlePointHold existing = battlePointHoldMapper.findByMatchAndUser(matchId, userId).orElse(null);
        if (existing != null && existing.getStatus() == BattleHoldStatus.HELD) {
            return;
        }
        if (existing != null && existing.getStatus() == BattleHoldStatus.SETTLED) {
            throw new BattleException(BattleErrorCode.SETTLEMENT_ALREADY_DONE);
        }

        try {
            ensureUserPointRow(userId);
        } catch (Exception e) {
            log.error("[battle] matchId={} userId={} action=hold ensureUserPoint error={}", matchId, userId, e.getMessage(), e);
            throw new BattleException(BattleErrorCode.POINT_ACCOUNT_MISSING);
        }

        try {
            int updated = pointMapper.usePoint(userId, amount);
            if (updated != 1) {
                throw new BattleException(BattleErrorCode.INSUFFICIENT_POINTS);
            }
        } catch (BattleException e) {
            throw e;
        } catch (Exception e) {
            log.error("[battle] matchId={} userId={} action=hold usePoint error={}", matchId, userId, e.getMessage(), e);
            throw new BattleException(BattleErrorCode.HOLD_DB_ERROR);
        }

        BattlePointHistoryRecord history = BattlePointHistoryRecord.builder()
                .userId(userId)
                .changeAmount(amount.negate())
                .type("BATTLE_HOLD")
                .paymentOrderId(null)
                .description("배틀 베팅 차감")
                .battleMatchId(matchId)
                .build();
        try {
            battlePointHistoryMapper.insert(history);
        } catch (DataAccessException e) {
            log.error("[battle] matchId={} userId={} action=hold history error={}", matchId, userId, e.getMessage(), e);
            throw new BattleException(BattleErrorCode.HOLD_DB_ERROR);
        }

        try {
            if (existing == null) {
                BattlePointHold hold = BattlePointHold.builder()
                        .matchId(matchId)
                        .userId(userId)
                        .amount(amount)
                        .status(BattleHoldStatus.HELD)
                        .pointHistoryId(history.getId())
                        .build();
                battlePointHoldMapper.insert(hold);
            } else {
                battlePointHoldMapper.updateStatus(matchId, userId, BattleHoldStatus.HELD.name(), history.getId());
            }
        } catch (DataAccessException e) {
            // duplicate hold row -> try idempotent update
            try {
                battlePointHoldMapper.updateStatus(matchId, userId, BattleHoldStatus.HELD.name(), history.getId());
                return;
            } catch (Exception ex) {
                log.error("[battle] matchId={} userId={} action=hold holdRow error={}", matchId, userId, ex.getMessage(), ex);
                throw new BattleException(BattleErrorCode.HOLD_DB_ERROR);
            }
        }

        log.info("[battle] matchId={} userId={} action=hold amount={}", matchId, userId, amount);
    }

    @Override
    public boolean settleWin(String matchId, Long winnerUserId, Long loserUserId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        BattlePointHold winnerHold = battlePointHoldMapper.findByMatchAndUser(matchId, winnerUserId).orElse(null);
        BattlePointHold loserHold = battlePointHoldMapper.findByMatchAndUser(matchId, loserUserId).orElse(null);

        if (winnerHold == null || loserHold == null) {
            log.warn("[battle] matchId={} settle skipped due to missing hold (winnerHold={}, loserHold={})",
                    matchId, winnerHold, loserHold);
            return false;
        }

        if (winnerHold.getStatus() == BattleHoldStatus.REFUNDED || loserHold.getStatus() == BattleHoldStatus.REFUNDED) {
            log.warn("[battle] matchId={} settle skipped because hold already refunded", matchId);
            return false;
        }
        if ((winnerHold != null && winnerHold.getStatus() == BattleHoldStatus.SETTLED)
                || (loserHold != null && loserHold.getStatus() == BattleHoldStatus.SETTLED)) {
            return true;
        }

        BigDecimal reward = amount.multiply(BigDecimal.valueOf(2));
        ensureUserPointRow(winnerUserId);
        pointMapper.addRewardPoint(winnerUserId, reward);

        BattlePointHistoryRecord history = BattlePointHistoryRecord.builder()
                .userId(winnerUserId)
                .changeAmount(reward)
                .type("BATTLE_REWARD")
                .paymentOrderId(null)
                .description("배틀 승리 보상")
                .battleMatchId(matchId)
                .build();
        try {
            battlePointHistoryMapper.insert(history);
        } catch (DataAccessException e) {
            log.error("[battle] matchId={} userId={} action=settle history error={}", matchId, winnerUserId, e.getMessage(), e);
            throw new BattleException(BattleErrorCode.HOLD_DB_ERROR);
        }

        try {
            battlePointHoldMapper.updateStatus(matchId, winnerUserId, BattleHoldStatus.SETTLED.name(), history.getId());
            battlePointHoldMapper.updateStatus(matchId, loserUserId, BattleHoldStatus.SETTLED.name(), loserHold.getPointHistoryId());
        } catch (DataAccessException e) {
            log.error("[battle] matchId={} userId={} action=settle holdRow error={}", matchId, winnerUserId, e.getMessage(), e);
            throw new BattleException(BattleErrorCode.HOLD_DB_ERROR);
        }

        log.info("[battle] matchId={} winnerUserId={} action=settle reward={}", matchId, winnerUserId, reward);
        return true;
    }

    @Override
    public boolean refund(String matchId, Long userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        BattlePointHold existing = battlePointHoldMapper.findByMatchAndUser(matchId, userId).orElse(null);
        if (existing == null) {
            log.warn("[battle] matchId={} userId={} refund skipped because hold is missing", matchId, userId);
            return false;
        }
        if (existing.getStatus() == BattleHoldStatus.REFUNDED) {
            return true;
        }
        if (existing.getStatus() == BattleHoldStatus.SETTLED) {
            log.warn("[battle] matchId={} userId={} refund skipped because already settled", matchId, userId);
            return false;
        }

        pointMapper.refundPoint(userId, amount);

        BattlePointHistoryRecord history = BattlePointHistoryRecord.builder()
                .userId(userId)
                .changeAmount(amount)
                .type("BATTLE_REFUND")
                .paymentOrderId(null)
                .description("배틀 취소/무승부 환불")
                .battleMatchId(matchId)
                .build();
        try {
            battlePointHistoryMapper.insert(history);
        } catch (DataAccessException e) {
            log.error("[battle] matchId={} userId={} action=refund history error={}", matchId, userId, e.getMessage(), e);
            throw new BattleException(BattleErrorCode.HOLD_DB_ERROR);
        }

        try {
            battlePointHoldMapper.updateStatus(matchId, userId, BattleHoldStatus.REFUNDED.name(), history.getId());
        } catch (DataAccessException e) {
            log.error("[battle] matchId={} userId={} action=refund holdRow error={}", matchId, userId, e.getMessage(), e);
            throw new BattleException(BattleErrorCode.HOLD_DB_ERROR);
        }

        log.info("[battle] matchId={} userId={} action=refund amount={}", matchId, userId, amount);
        return true;
    }

    @Override
    public BigDecimal getBalance(Long userId) {
        if (userId == null) {
            return BigDecimal.ZERO;
        }
        UserPoint userPoint = pointMapper.findUserPointByUserId(userId).orElse(null);
        if (userPoint == null) {
            return BigDecimal.ZERO;
        }
        return userPoint.getBalance() == null ? BigDecimal.ZERO : userPoint.getBalance();
    }

    private void ensureUserPointRow(Long userId) {
        UserPoint userPoint = pointMapper.findUserPointByUserId(userId).orElse(null);
        if (Objects.isNull(userPoint)) {
            UserPoint newRow = UserPoint.builder()
                    .userId(userId)
                    .balance(BigDecimal.ZERO)
                    .build();
            pointMapper.insertUserPoint(newRow);
        }
    }
}
