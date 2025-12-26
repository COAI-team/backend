package kr.or.kosa.backend.battle.service;

import kr.or.kosa.backend.battle.domain.BattleParticipantState;
import kr.or.kosa.backend.battle.domain.BattleRoomState;
import kr.or.kosa.backend.battle.domain.BattleSettlementStatus;
import kr.or.kosa.backend.battle.dto.BattleFinishParticipantResponse;
import kr.or.kosa.backend.battle.dto.BattleFinishResponse;
import kr.or.kosa.backend.battle.port.BattlePointPort;
import kr.or.kosa.backend.battle.port.BattleUserPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class BattleSettlementService {

    private final BattlePointPort battlePointPort;
    private final BattleMatchService battleMatchService;

    @Autowired(required = false)
    private BattleUserPort battleUserPort;

    /* ========================================
     * 기존 메서드 (Hold/Settle/Refund)
     * ======================================== */

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

    /* ========================================
     * 새로 추가: 정산 결과 데이터 생성
     * ======================================== */

    /**
     * 게임 정산 처리 및 결과 반환
     * - 포인트 실제 이동 처리
     * - 프론트엔드에 표시할 정산 데이터 생성
     */
    @Transactional
    public BattleFinishResponse settleAndGetResult(BattleRoomState state) {
        if (state == null) {
            throw new IllegalArgumentException("room state is null");
        }

        log.info("[정산] 시작 - roomId: {}, matchId: {}, 승자: {}, 사유: {}",
                state.getRoomId(), state.getMatchId(), state.getWinnerUserId(), state.getWinReason());

        Long hostUserId = state.getHostUserId();
        Long guestUserId = state.getGuestUserId();
        Long winnerUserId = state.getWinnerUserId();
        String winReason = state.getWinReason();
        BigDecimal betAmount = state.getBetAmount() != null ? state.getBetAmount() : BigDecimal.ZERO;

        boolean isRefund = winnerUserId == null
                || "TIMEOUT_DRAW".equals(winReason)
                || "REFUNDED".equals(winReason)
                || "CANCELED".equals(winReason);

        if (isRefund) {
            refundAll(state);
        } else {
            Long loserUserId = null;
            if (hostUserId != null && guestUserId != null) {
                loserUserId = winnerUserId.equals(hostUserId) ? guestUserId : hostUserId;
            }

            if (loserUserId != null) {
                settle(state.getMatchId(), winnerUserId, loserUserId, betAmount);
            }
        }

        BattleFinishParticipantResponse hostResponse = null;
        BattleFinishParticipantResponse guestResponse = null;
        if (hostUserId != null) {
            hostResponse = createParticipantResponse(state, hostUserId, winnerUserId, betAmount, isRefund);
        }
        if (guestUserId != null) {
            guestResponse = createParticipantResponse(state, guestUserId, winnerUserId, betAmount, isRefund);
        }

        log.info("[정산] 완료 - 환불: {}", isRefund);

        return BattleFinishResponse.builder()
                .matchId(state.getMatchId())
                .host(hostResponse)
                .guest(guestResponse)
                .winnerUserId(winnerUserId)
                .winReason(winReason != null ? winReason : "UNKNOWN")
                .betAmount(betAmount)
                .build();
    }

    /**
     * 개별 참가자 정산 응답 생성
     */
    private BattleFinishParticipantResponse createParticipantResponse(
            BattleRoomState state,
            Long userId,
            Long winnerUserId,
            BigDecimal betAmount,
            boolean isRefund) {

        BattleParticipantState participant = state.participant(userId);
        if (participant == null) {
            log.warn("[정산] 참가자 상태 없음 - userId: {}", userId);
            participant = BattleParticipantState.builder()
                    .userId(userId)
                    .nickname(fetchNickname(userId))
                    .finished(false)
                    .build();
        }

        boolean isWinner = userId.equals(winnerUserId);
        boolean solved = participant.isFinished();

        Integer grade = participant.getGrade();
        if (grade == null) {
            grade = fetchGrade(userId);
        }

        BigDecimal baseAmount = BigDecimal.ZERO;
        BigDecimal bonusAmount = BigDecimal.ZERO;
        BigDecimal totalChange = BigDecimal.ZERO;

        if (isRefund) {
            baseAmount = BigDecimal.ZERO;
            bonusAmount = BigDecimal.ZERO;
            totalChange = BigDecimal.ZERO;
        } else if (betAmount.compareTo(BigDecimal.ZERO) > 0) {
            if (isWinner) {
                baseAmount = betAmount;
                totalChange = betAmount;

                if (solved) {
                    Duration solveDuration = calculateSolveDuration(state, participant);
                    if (solveDuration != null) {
                        long minutes = solveDuration.toMinutes();
                        if (minutes < 5) {
                            bonusAmount = betAmount.multiply(BigDecimal.valueOf(0.1));
                            totalChange = totalChange.add(bonusAmount);
                        }
                    }
                }
            } else {
                baseAmount = betAmount.negate();
                totalChange = betAmount.negate();
            }
        }

        BigDecimal currentBalance = fetchPointBalance(userId);
        BigDecimal newBalance = currentBalance.add(totalChange);

        Duration elapsed = calculateSolveDuration(state, participant);
        Integer elapsedSeconds = elapsed != null ? (int) elapsed.getSeconds() : null;
        Long elapsedMs = elapsed != null ? elapsed.toMillis() : null;

        BigDecimal baseScore = participant.getBaseScore() != null ? participant.getBaseScore() : BigDecimal.ZERO;
        BigDecimal bonusScore = participant.getTimeBonus() != null ? participant.getTimeBonus() : BigDecimal.ZERO;
        BigDecimal finalScore = participant.getFinalScore() != null ? participant.getFinalScore() : BigDecimal.ZERO;

        log.info("[정산] userId: {}, 승리: {}, 해결: {}, base: {}, bonus: {}, total: {}, 잔액: {}",
                userId, isWinner, solved, baseAmount, bonusAmount, totalChange, newBalance);

        return BattleFinishParticipantResponse.builder()
                .userId(userId)
                .nickname(participant.getNickname())
                .grade(grade)
                .solved(solved)
                .isWinner(isWinner)
                .baseAmount(baseAmount)
                .bonusAmount(bonusAmount)
                .totalChange(totalChange)
                .pointBalance(newBalance)
                .elapsedSeconds(elapsedSeconds)
                .baseScore(baseScore)
                .bonusScore(bonusScore)
                .finalScore(finalScore)
                .elapsedMs(elapsedMs)
                .judgeMessage(participant.getJudgeMessage())
                .build();
    }

    /**
     * 문제 해결 시간 계산
     */
    private Duration calculateSolveDuration(BattleRoomState state, BattleParticipantState participant) {
        if (state.getStartedAt() == null) {
            return null;
        }

        if (participant != null && participant.isFinished() && participant.getLastSubmittedAt() != null) {
            return Duration.between(state.getStartedAt(), participant.getLastSubmittedAt());
        }

        if (state.getFinishedAt() != null) {
            return Duration.between(state.getStartedAt(), state.getFinishedAt());
        }

        return null;
    }

    /**
     * 닉네임 조회
     */
    private String fetchNickname(Long userId) {
        if (battleUserPort == null) {
            log.warn("[정산] BattleUserPort가 null - userId: {}", userId);
            return "사용자#" + userId;
        }

        try {
            return battleUserPort.findProfile(userId)
                    .map(profile -> profile.getNickname())
                    .filter(nickname -> nickname != null && !nickname.isBlank())
                    .orElse("사용자#" + userId);
        } catch (Exception e) {
            log.warn("[정산] 닉네임 조회 실패 - userId: {}", userId, e);
        }

        return "사용자#" + userId;
    }

    private Integer fetchGrade(Long userId) {
        if (battleUserPort == null) {
            return null;
        }
        try {
            return battleUserPort.findProfile(userId)
                    .map(profile -> profile.getGrade())
                    .orElse(null);
        } catch (Exception e) {
            log.warn("[정산] 등급 조회 실패 - userId: {}", userId, e);
        }
        return null;
    }

    /**
     * 포인트 잔액 조회
     */
    private BigDecimal fetchPointBalance(Long userId) {
        try {
            return battlePointPort.getBalance(userId);
        } catch (Exception e) {
            log.error("[정산] 포인트 잔액 조회 실패 - userId: {}", userId, e);
            return BigDecimal.ZERO;
        }
    }
}




