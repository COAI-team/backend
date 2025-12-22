package kr.or.kosa.backend.battle.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import kr.or.kosa.backend.battle.domain.BattleMatch;
import kr.or.kosa.backend.battle.domain.BattleRoomState;
import kr.or.kosa.backend.battle.domain.BattleStatus;
import kr.or.kosa.backend.battle.mapper.BattleMatchMapper;
import kr.or.kosa.backend.battle.util.BattleDurationPolicy;
import kr.or.kosa.backend.battle.util.BattleRedisKeyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BattleRecoveryService {

    private static final Duration COUNTDOWN_GRACE = Duration.ofMinutes(2);

    private final BattleMatchMapper battleMatchMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final BattleSettlementService battleSettlementService;
    private final BattleMatchService battleMatchService;
    private final BattleMessageService battleMessageService;
    private final BattlePenaltyService battlePenaltyService;
    private final BattleDurationPolicy battleDurationPolicy;

    @Scheduled(fixedDelay = 60_000L)
    public void recoverLostMatches() {
        List<BattleMatch> active = battleMatchMapper.findActiveMatches();
        Instant now = Instant.now();
        for (BattleMatch match : active) {
            if (match.getStatus() == BattleStatus.COUNTDOWN) {
                handleStuckCountdown(match, now);
            } else if (match.getStatus() == BattleStatus.RUNNING) {
                handleRunningTimeout(match, now);
            }
        }
    }

    private void handleStuckCountdown(BattleMatch match, Instant now) {
        Instant startedAt = match.getStartedAt() != null ? match.getStartedAt().atZone(java.time.ZoneId.systemDefault()).toInstant() : null;
        if (startedAt == null) {
            return;
        }
        if (startedAt.isAfter(now.minus(COUNTDOWN_GRACE))) {
            return;
        }
        BattleRoomState state = buildState(match);
        battleSettlementService.refundAll(state);
        battleMatchService.markCanceled(match.getMatchId());
        redisTemplate.delete(BattleRedisKeyUtil.roomKey(state.getRoomId()));
        battleMessageService.publishFinish(state);
        log.warn("[battle-recover] canceled stuck countdown matchId={} startedAt={}", match.getMatchId(), startedAt);
    }

    private void handleRunningTimeout(BattleMatch match, Instant now) {
        Instant startedAt = match.getStartedAt() != null ? match.getStartedAt().atZone(java.time.ZoneId.systemDefault()).toInstant() : null;
        if (startedAt == null) {
            return;
        }
        int limitMinutes = battleDurationPolicy.effectiveMinutes(match.getMaxDurationMinutes(), null);
        Instant deadline = startedAt.plus(Duration.ofMinutes(limitMinutes));
        if (deadline.isAfter(now)) {
            return;
        }
        BattleRoomState state = buildState(match);
        state.setStatus(BattleStatus.FINISHED);
        state.setFinishedAt(Instant.now());
        state.setWinReason("TIMEOUT");
        battleSettlementService.refundAll(state);
        battleMatchService.finishMatch(match.getMatchId(), null, "TIMEOUT");
        redisTemplate.delete(BattleRedisKeyUtil.roomKey(state.getRoomId()));
        battleMessageService.publishFinish(state);
        battlePenaltyService.recordNormalFinish(state.getHostUserId());
        battlePenaltyService.recordNormalFinish(state.getGuestUserId());
        log.warn("[battle-recover] timed out running matchId={} startedAt={} limitMin={}", match.getMatchId(), startedAt, limitMinutes);
    }

    private BattleRoomState buildState(BattleMatch match) {
        BattleRoomState state = new BattleRoomState();
        state.setRoomId(match.getMatchId());
        state.setMatchId(match.getMatchId());
        state.setHostUserId(match.getHostUserId());
        state.setGuestUserId(match.getGuestUserId());
        state.setBetAmount(match.getBetAmount());
        state.setMaxDurationMinutes(match.getMaxDurationMinutes());
        state.setStatus(match.getStatus());
        return state;
    }
}
