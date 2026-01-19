package kr.or.kosa.backend.battle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import kr.or.kosa.backend.battle.domain.BattleMatch;
import kr.or.kosa.backend.battle.domain.BattleParticipantState;
import kr.or.kosa.backend.battle.domain.BattleRoomState;
import kr.or.kosa.backend.battle.domain.BattleStatus;
import kr.or.kosa.backend.battle.mapper.BattleMatchMapper;
import kr.or.kosa.backend.battle.util.BattleDurationPolicy;
import kr.or.kosa.backend.battle.util.BattleRedisKeyUtil;
import kr.or.kosa.backend.battle.util.BattleTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BattleRecoveryService {

    private static final Duration COUNTDOWN_GRACE = Duration.ofMinutes(2);
    private static final Duration POST_GAME_HOLD = Duration.ofSeconds(30);

    private final BattleMatchMapper battleMatchMapper;
    @org.springframework.beans.factory.annotation.Autowired
    @Qualifier("battleRedisTemplate")
    private RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final BattleSettlementService battleSettlementService;
    private final BattleMatchService battleMatchService;
    private final BattleMessageService battleMessageService;
    private final BattlePenaltyService battlePenaltyService;
    private final BattleDurationPolicy battleDurationPolicy;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 60_000L)
    public void recoverLostMatches() {
        List<BattleMatch> active = battleMatchMapper.findActiveMatches();

        // 항상 KST LocalDateTime 기준
        LocalDateTime now = BattleTime.nowKst();

        for (BattleMatch match : active) {
            if (match.getStatus() == BattleStatus.COUNTDOWN) {
                handleStuckCountdown(match, now);
            } else if (match.getStatus() == BattleStatus.RUNNING) {
                handleRunningTimeout(match, now);
            }
        }
    }

    private void handleStuckCountdown(BattleMatch match, LocalDateTime now) {
        BattleRoomState state = buildStatePreferRedis(match);
        LocalDateTime countdownAt = match.getCountdownStartedAt();
        if (countdownAt == null) {
            countdownAt = state.getStartedAt();
        }
        if (countdownAt == null) {
            countdownAt = match.getStartedAt();
        }
        if (countdownAt == null) {
            return;
        }

        if (now.isBefore(countdownAt.plus(COUNTDOWN_GRACE))) {
            return;
        }

        state.setStatus(BattleStatus.CANCELED);
        state.setFinishedAt(now);
        state.setPostGameUntil(now.plusSeconds(POST_GAME_HOLD.toSeconds()));
        state.setWinReason("CANCELED");
        state.setWinnerUserId(null);

        battleSettlementService.refundAll(state);
        battleMatchService.markCanceled(match.getMatchId());

        upsertRoomForPostGame(state);

        battleMessageService.publishFinish(state);
        log.warn("[battle-recover] canceled stuck countdown matchId={} countdownAt={}", match.getMatchId(), countdownAt);
    }

    private void handleRunningTimeout(BattleMatch match, LocalDateTime now) {
        BattleRoomState state = buildStatePreferRedis(match);
        LocalDateTime startedAt = state.getStartedAt();
        if (startedAt == null) {
            startedAt = match.getStartedAt();
            if (startedAt != null) {
                state.setStartedAt(startedAt);
            }
        }
        if (startedAt == null) {
            return;
        }

        int limitMinutes = battleDurationPolicy.effectiveMinutes(match.getMaxDurationMinutes(), null);
        LocalDateTime deadline = startedAt.plusMinutes(limitMinutes);
        if (now.isBefore(deadline)) {
            return;
        }

        state.setStatus(BattleStatus.FINISHED);
        state.setFinishedAt(now);
        state.setPostGameUntil(now.plusSeconds(POST_GAME_HOLD.toSeconds()));
        state.setWinReason("TIMEOUT");
        state.setWinnerUserId(null);

        setTimeoutScores(state);

        battleSettlementService.refundAll(state);
        battleMatchService.finishMatch(match.getMatchId(), null, "TIMEOUT");

        upsertRoomForPostGame(state);

        battleMessageService.publishFinish(state);
        battlePenaltyService.recordNormalFinish(state.getHostUserId());
        battlePenaltyService.recordNormalFinish(state.getGuestUserId());

        log.warn("[battle-recover] timed out running matchId={} startedAt={} limitMin={}",
                match.getMatchId(), startedAt, limitMinutes);
    }

    private void setTimeoutScores(BattleRoomState state) {
        if (state.getHostUserId() != null) {
            BattleParticipantState host = state.participant(state.getHostUserId());
            if (host != null) {
                host.setBaseScore(java.math.BigDecimal.ZERO);
                host.setTimeBonus(java.math.BigDecimal.ZERO);
                host.setFinalScore(java.math.BigDecimal.ZERO);
                state.addOrUpdateParticipant(host);
            }
        }
        if (state.getGuestUserId() != null) {
            BattleParticipantState guest = state.participant(state.getGuestUserId());
            if (guest != null) {
                guest.setBaseScore(java.math.BigDecimal.ZERO);
                guest.setTimeBonus(java.math.BigDecimal.ZERO);
                guest.setFinalScore(java.math.BigDecimal.ZERO);
                state.addOrUpdateParticipant(guest);
            }
        }
    }

    private BattleRoomState buildStatePreferRedis(BattleMatch match) {
        String roomId = resolveRoomId(match.getMatchId());
        String key = roomId != null ? BattleRedisKeyUtil.roomKey(roomId) : null;

        BattleRoomState state = null;
        try {
            if (key != null) {
                Object cached = redisTemplate.opsForValue().get(key);
                if (cached instanceof BattleRoomState s) {
                    state = s;
                } else if (cached instanceof java.util.Map<?, ?> map) {
                    try {
                        state = objectMapper.convertValue(map, BattleRoomState.class);
                    } catch (IllegalArgumentException e) {
                        log.warn("[battle-recover] roomId={} action=deserialize-state error={}", roomId, e.getMessage());
                    }
                }
            }
        } catch (Exception ignore) {
        }

        if (state == null) {
            state = new BattleRoomState();
        }

        state.setRoomId(roomId != null ? roomId : match.getMatchId());
        state.setMatchId(match.getMatchId());
        state.setHostUserId(match.getHostUserId());
        state.setGuestUserId(match.getGuestUserId());
        state.setBetAmount(match.getBetAmount());
        state.setMaxDurationMinutes(match.getMaxDurationMinutes());
        state.setStatus(match.getStatus());
        return state;
    }

    private String resolveRoomId(String matchId) {
        if (matchId == null) return null;
        String mapped = stringRedisTemplate.opsForValue().get(BattleRedisKeyUtil.matchRoomKey(matchId));
        if (mapped != null && !mapped.isBlank()) {
            return mapped;
        }

        try {
            Boolean exists = redisTemplate.hasKey(BattleRedisKeyUtil.roomKey(matchId));
            if (Boolean.TRUE.equals(exists)) {
                return matchId;
            }
        } catch (Exception ignored) {
        }

        try {
            String found = redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<String>) connection -> {
                org.springframework.data.redis.core.ScanOptions options =
                        org.springframework.data.redis.core.ScanOptions.scanOptions()
                                .match(BattleRedisKeyUtil.roomKey("*"))
                                .count(200)
                                .build();
                org.springframework.data.redis.serializer.RedisSerializer<?> serializer = redisTemplate.getValueSerializer();
                try (org.springframework.data.redis.core.Cursor<byte[]> cursor = connection.scan(options)) {
                    while (cursor.hasNext()) {
                        byte[] key = cursor.next();
                        byte[] raw = connection.get(key);
                        if (raw == null) continue;
                        Object cached = serializer.deserialize(raw);
                        BattleRoomState state = null;
                        if (cached instanceof BattleRoomState s) {
                            state = s;
                        } else if (cached instanceof java.util.Map<?, ?> map) {
                            try {
                                state = objectMapper.convertValue(map, BattleRoomState.class);
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                        if (state != null && matchId.equals(state.getMatchId())) {
                            return state.getRoomId();
                        }
                    }
                }
                return null;
            });
            if (found != null && !found.isBlank()) {
                stringRedisTemplate.opsForValue().set(BattleRedisKeyUtil.matchRoomKey(matchId), found);
                return found;
            }
        } catch (Exception e) {
            log.warn("[battle-recover] matchId={} action=resolveRoomId error={}", matchId, e.getMessage());
        }

        return null;
    }

    private void upsertRoomForPostGame(BattleRoomState state) {
        if (state == null || state.getRoomId() == null) return;
        String key = BattleRedisKeyUtil.roomKey(state.getRoomId());
        try {
            Boolean exists = redisTemplate.hasKey(key);
            if (!Boolean.TRUE.equals(exists)) {
                return;
            }
            redisTemplate.opsForValue().set(key, state, POST_GAME_HOLD);
        } catch (Exception e) {
            log.warn("[battle-recover] failed to upsert postgame room state roomId={} err={}",
                    state.getRoomId(), e.toString());
        }
    }
}

