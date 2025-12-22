package kr.or.kosa.backend.battle.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;
import java.math.RoundingMode;

import kr.or.kosa.backend.battle.domain.BattleParticipantState;
import kr.or.kosa.backend.battle.domain.BattleRoomState;
import kr.or.kosa.backend.battle.domain.BattleStatus;
import kr.or.kosa.backend.battle.dto.BattleRoomCreateRequest;
import kr.or.kosa.backend.battle.dto.BattleRoomJoinRequest;
import kr.or.kosa.backend.battle.dto.BattleRoomResponse;
import kr.or.kosa.backend.battle.dto.BattleRoomUpdateRequest;
import kr.or.kosa.backend.battle.dto.BattleSubmitMessage;
import kr.or.kosa.backend.battle.dto.BattleSubmitResultResponse;
import kr.or.kosa.backend.battle.exception.BattleErrorCode;
import kr.or.kosa.backend.battle.exception.BattleException;
import kr.or.kosa.backend.battle.port.BattleUserPort;
import kr.or.kosa.backend.battle.port.BattlePointPort;
import kr.or.kosa.backend.battle.port.dto.BattleJudgeResult;
import kr.or.kosa.backend.battle.port.dto.BattleUserProfile;
import kr.or.kosa.backend.battle.util.BattleDurationPolicy;
import kr.or.kosa.backend.battle.util.BattleRedisKeyUtil;
import kr.or.kosa.backend.battle.util.BattleValidator;
import kr.or.kosa.backend.battle.util.RedisLockManager;
import kr.or.kosa.backend.algorithm.mapper.AlgorithmProblemMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
@RequiredArgsConstructor
public class BattleRoomService {
    private static final Logger log = LoggerFactory.getLogger(BattleRoomService.class);

    private static final int COUNTDOWN_SECONDS = 5;
    private static final Duration SUBMIT_COOLDOWN = Duration.ofSeconds(2);
    private static final Duration POSTGAME_LOCK_DURATION = Duration.ofSeconds(30);
    private static final int PASSWORD_ATTEMPT_LIMIT = 5;
    private static final Duration PASSWORD_ATTEMPT_WINDOW = Duration.ofMinutes(1);
    private static final Duration PASSWORD_LOCK_DURATION = Duration.ofMinutes(5);

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisLockManager redisLockManager;
    private final BattleValidator battleValidator;
    private final BattleMatchService battleMatchService;
    private final BattleSettlementService battleSettlementService;
    private final BattleMessageService battleMessageService;
    private final BattleJudgeService battleJudgeService;
    private final BattlePenaltyService battlePenaltyService;
    private final BattlePointPort battlePointPort;
    private final BattleDurationPolicy battleDurationPolicy;
    private final AlgorithmProblemMapper algorithmProblemMapper;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private BattleUserPort battleUserPort;
    private final PasswordEncoder passwordEncoder;
    @Qualifier("battleTaskScheduler")
    private final TaskScheduler battleTaskScheduler;

    private final ConcurrentHashMap<String, ScheduledFuture<?>> timeoutTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> disconnectTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> postGameResetTasks = new ConcurrentHashMap<>();

    private void ensureNicknames(BattleRoomState state) {
        if (state == null) return;
        Set<Long> ids = new java.util.HashSet<>();
        if (state.getHostUserId() != null) ids.add(state.getHostUserId());
        if (state.getGuestUserId() != null) ids.add(state.getGuestUserId());
        if (state.getParticipants() != null) {
            ids.addAll(state.getParticipants().keySet());
        }
        for (Long id : ids) {
            if (id == null) continue;
            BattleParticipantState participant = state.participant(id);
            if (participant == null) {
                participant = BattleParticipantState.builder().userId(id).ready(false).build();
            }
            if (participant.getNickname() == null || participant.getNickname().isBlank()) {
                participant.setNickname(fetchNickname(id));
            }
            participant.setPointBalance(fetchPointBalance(id));
            state.addOrUpdateParticipant(participant);
        }
    }

    public BattleRoomResponse createRoom(Long userId, BattleRoomCreateRequest request) {
        if (battlePenaltyService.hasPenalty(userId)) {
            throw new BattleException(BattleErrorCode.INVALID_STATUS);
        }
        if (request.getBetAmount() == null) {
            request.setBetAmount(java.math.BigDecimal.ZERO);
        }
        if (request.getLevelMode() == null || request.getLevelMode().isBlank()) {
            request.setLevelMode("ANY");
        }
        battleValidator.validateBetAmount(request.getBetAmount());
        String userLock = redisLockManager.lock(BattleRedisKeyUtil.userLockKey(userId));
        if (userLock == null) {
            throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);
        }
        try {
            String existingRoomId = stringRedisTemplate.opsForValue().get(BattleRedisKeyUtil.activeRoomKey(userId));
            if (existingRoomId != null) {
                Optional<BattleRoomState> existing = getRoomState(existingRoomId);
                if (existing.isPresent() && isActive(existing.get()) && isParticipant(existing.get(), userId)) {
                    log.info("[battle] matchId={} userId={} action=create idempotent", existing.get().getMatchId(), userId);
                    return BattleRoomResponse.from(existing.get());
                }
                cleanupActiveMapping(userId, existingRoomId, existing.orElse(null));
            }

            Integer maxMinutes = battleDurationPolicy.effectiveMinutes(request.getMaxDurationMinutes(), findProblemDifficulty(request.getAlgoProblemId()));
            Instant now = Instant.now();
            String matchId = UUID.randomUUID().toString();
            String password = request.getPassword();
            boolean isPrivate = password != null && !password.isBlank();
            if (isPrivate && !password.matches("^\\d{4}$")) {
                throw new BattleException(BattleErrorCode.INVALID_STATUS);
            }
            String passwordHash = isPrivate ? passwordEncoder.encode(password) : null;

            BattleParticipantState host = BattleParticipantState.builder()
                    .userId(userId)
                    .nickname(fetchNickname(userId))
                    .ready(false)
                    .build();

            BattleRoomState state = BattleRoomState.builder()
                    .roomId(matchId)
                    .matchId(matchId)
                    .title(request.getTitle())
                    .status(BattleStatus.WAITING)
                    .hostUserId(userId)
                    .algoProblemId(request.getAlgoProblemId())
                    .languageId(request.getLanguageId())
                    .levelMode(request.getLevelMode())
                    .betAmount(request.getBetAmount())
                    .maxDurationMinutes(maxMinutes)
                    .isPrivate(isPrivate)
                    .passwordHash(passwordHash)
                    .createdAt(now)
                    
                    .build();
            state.addOrUpdateParticipant(host);

            try {
                battleMatchService.createMatch(state);
                saveRoom(state);
                addRoomToLobby(state.getRoomId());
                addMember(state.getRoomId(), userId);
                setActiveRoom(userId, state.getRoomId());
                broadcastLobby();
                battleMessageService.publishRoomState(state);
            } catch (Exception e) {
                cleanupRoomKeys(state.getRoomId(), Set.of(userId));
                throw e;
            }

            log.info("[battle] matchId={} userId={} action=create state={}", state.getMatchId(), userId, state.getStatus());
            return BattleRoomResponse.from(state);
        } finally {
            redisLockManager.unlock(BattleRedisKeyUtil.userLockKey(userId), userLock);
        }
    }

    public List<BattleRoomResponse> listRooms() {
        Set<String> roomIds = stringRedisTemplate.opsForSet().members(BattleRedisKeyUtil.lobbyKey());
        if (roomIds == null || roomIds.isEmpty()) {
            return List.of();
        }
        List<BattleRoomResponse> rooms = new ArrayList<>();
        for (String roomId : roomIds) {
            Optional<BattleRoomState> stateOpt = getRoomState(roomId);
            if (stateOpt.isEmpty()) {
                removeRoomFromLobby(roomId);
                continue;
            }
            BattleRoomState state = stateOpt.get();
            ensureNicknames(state);
            if (state.getStatus() == BattleStatus.WAITING || state.getStatus() == BattleStatus.COUNTDOWN) {
                try {
                    rooms.add(BattleRoomResponse.from(state));
                } catch (Exception e) {
                    log.warn("[battle] roomId={} action=listRooms response-error={}", roomId, e.getMessage());
                }
            }
        }
        return rooms;
    }

    public BattleRoomResponse getRoom(String roomId, Long requesterId) {
        BattleRoomState state = getRoomState(roomId).orElseThrow(() -> new BattleException(BattleErrorCode.ROOM_NOT_FOUND));
        ensureNicknames(state);
        return BattleRoomResponse.from(state);
    }

    public BattleRoomResponse joinRoom(String roomId, Long userId, String password) {
        if (battlePenaltyService.hasPenalty(userId)) {
            throw new BattleException(BattleErrorCode.INVALID_STATUS);
        }
        String userLock = redisLockManager.lock(BattleRedisKeyUtil.userLockKey(userId));
        if (userLock == null) {
            throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);
        }
        try {
            String activeRoomId = stringRedisTemplate.opsForValue().get(BattleRedisKeyUtil.activeRoomKey(userId));
            if (activeRoomId != null && !Objects.equals(activeRoomId, roomId)) {
                Optional<BattleRoomState> existing = getRoomState(activeRoomId);
                if (existing.isPresent() && isActive(existing.get()) && isParticipant(existing.get(), userId)) {
                    log.info("[battle] matchId={} userId={} action=join idempotent-existing", existing.get().getMatchId(), userId);
                    return BattleRoomResponse.from(existing.get());
                }
                cleanupActiveMapping(userId, activeRoomId, existing.orElse(null));
            } else if (activeRoomId != null && Objects.equals(activeRoomId, roomId)) {
                Optional<BattleRoomState> same = getRoomState(roomId);
                if (same.isPresent() && isActive(same.get()) && isParticipant(same.get(), userId)) {
                    log.info("[battle] matchId={} userId={} action=join idempotent-same", same.get().getMatchId(), userId);
                    return BattleRoomResponse.from(same.get());
                }
                cleanupActiveMapping(userId, roomId, same.orElse(null));
            }

            String roomLock = redisLockManager.lock(BattleRedisKeyUtil.lockKey(roomId));
            if (roomLock == null) {
                throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);
            }
            try {
                BattleRoomState state = getRoomState(roomId).orElseThrow(() -> new BattleException(BattleErrorCode.ROOM_NOT_FOUND));
                if (state.getStatus() == BattleStatus.FINISHED
                        && state.getPostGameUntil() != null
                        && state.getPostGameUntil().isAfter(Instant.now())
                        && !isParticipant(state, userId)) {
                    throw new BattleException(BattleErrorCode.POSTGAME_LOCK);
                }
                if (Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(BattleRedisKeyUtil.kickedKey(roomId), String.valueOf(userId)))) {
                    throw new BattleException(BattleErrorCode.KICKED_REJOIN_BLOCKED);
                }
                if (state.isPrivate()) {
                    enforcePasswordRateLimit(roomId, userId);
                    if (password == null || !password.matches("^\\d{4}$") || !passwordEncoder.matches(password, state.getPasswordHash())) {
                        registerPasswordFailure(roomId, userId);
                        randomDelay();
                        throw new BattleException(BattleErrorCode.INVALID_PASSWORD);
                    }
                    clearPasswordAttempts(roomId, userId);
                }
                boolean alreadyParticipant = isParticipant(state, userId);
                if (state.getStatus() == BattleStatus.RUNNING && alreadyParticipant) {
                    cancelDisconnectGrace(state.getRoomId(), userId);
                    state.addOrUpdateParticipant(state.participant(userId));
                    saveRoom(state);
                    setActiveRoom(userId, roomId);
                    addMember(roomId, userId);
                    battleMessageService.publishRoomState(state);
                    return BattleRoomResponse.from(state);
                }
                if (alreadyParticipant) {
                    return BattleRoomResponse.from(state);
                }
                if (state.getStatus() != BattleStatus.WAITING) {
                    throw new BattleException(BattleErrorCode.JOIN_NOT_ALLOWED);
                }
                if (state.getGuestUserId() != null) {
                    throw new BattleException(BattleErrorCode.ROOM_FULL);
                }

                    state.setGuestUserId(userId);
                    state.addOrUpdateParticipant(BattleParticipantState.builder()
                            .userId(userId)
                            .nickname(fetchNickname(userId))
                            .ready(false)
                            .build());

                saveRoom(state);
                addMember(roomId, userId);
                setActiveRoom(userId, roomId);
                battleMatchService.updateParticipants(state.getMatchId(), state.getHostUserId(), state.getGuestUserId());
                ensureNicknames(state);
                broadcastLobby();
                battleMessageService.publishRoomState(state);

                log.info("[battle] matchId={} userId={} action=join state={}", state.getMatchId(), userId, state.getStatus());
                return BattleRoomResponse.from(state);
            } finally {
                redisLockManager.unlock(BattleRedisKeyUtil.lockKey(roomId), roomLock);
            }
        } finally {
            redisLockManager.unlock(BattleRedisKeyUtil.userLockKey(userId), userLock);
        }
    }

    public BattleRoomResponse leaveRoom(String roomId, Long userId) {
        String userLock = redisLockManager.lock(BattleRedisKeyUtil.userLockKey(userId));
        if (userLock == null) {
            throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);
        }
        try {
            String lockToken = redisLockManager.lock(BattleRedisKeyUtil.lockKey(roomId));
            if (lockToken == null) {
                throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);
            }
            try {
                BattleRoomState state = getRoomState(roomId).orElseThrow(() -> new BattleException(BattleErrorCode.ROOM_NOT_FOUND));
                if (!isParticipant(state, userId)) {
                    throw new BattleException(BattleErrorCode.NOT_PARTICIPANT);
                }

                if (state.getStatus() == BattleStatus.COUNTDOWN) {
                    throw new BattleException(BattleErrorCode.INVALID_STATUS);
                }

                if (state.getStatus() == BattleStatus.WAITING || state.getStatus() == BattleStatus.COUNTDOWN) {
                    if (Objects.equals(userId, state.getHostUserId())) {
                        Long guestId = state.getGuestUserId();
                        if (guestId != null) {
                            // promote guest to host
                            state.setHostUserId(guestId);
                            state.setGuestUserId(null);
                            Optional.ofNullable(state.getParticipants()).ifPresent(map -> map.remove(userId));
                            BattleParticipantState newHost = state.participant(guestId);
                            if (newHost != null) {
                                newHost.setReady(false);
                                state.addOrUpdateParticipant(newHost);
                            }
                            state.setCountdownStarted(false);
                            state.setStatus(BattleStatus.WAITING);
                            saveRoom(state);
                            clearActiveRoom(userId, roomId);
                            removeMember(roomId, userId);
                            setActiveRoom(guestId, roomId);
                            addMember(roomId, guestId);
                            ensureNicknames(state);
                            addRoomToLobby(roomId);
                            broadcastLobby();
                            battleMessageService.publishRoomState(state);
                            stringRedisTemplate.opsForSet().remove(BattleRedisKeyUtil.kickedKey(roomId), String.valueOf(guestId));
                        } else {
                            // no participants remain -> cleanup
                            Optional.ofNullable(state.getParticipants()).ifPresent(map -> map.remove(userId));
                            clearActiveRoom(userId, roomId);
                            removeMember(roomId, userId);
                            cleanupRoomKeys(roomId, Set.of(userId));
                        }
                    } else {
                        state.setGuestUserId(null);
                        Optional.ofNullable(state.getParticipants()).ifPresent(map -> map.remove(userId));
                        state.setCountdownStarted(false);
                        state.setStatus(BattleStatus.WAITING);
                        saveRoom(state);
                        clearActiveRoom(userId, roomId);
                        removeMember(roomId, userId);
                        ensureNicknames(state);
                        addRoomToLobby(roomId);
                        broadcastLobby();
                        battleMessageService.publishRoomState(state);
                    }
                } else if (state.getStatus() == BattleStatus.RUNNING) {
                    startDisconnectGrace(state, userId);
                }

                log.info("[battle] matchId={} userId={} action=leave state={}", state.getMatchId(), userId, state.getStatus());
                return BattleRoomResponse.from(state);
            } finally {
                redisLockManager.unlock(BattleRedisKeyUtil.lockKey(roomId), lockToken);
            }
        } finally {
            redisLockManager.unlock(BattleRedisKeyUtil.userLockKey(userId), userLock);
        }
    }

    public BattleRoomResponse kickGuest(String roomId, Long hostUserId) {
        String userLock = redisLockManager.lock(BattleRedisKeyUtil.userLockKey(hostUserId));
        if (userLock == null) {
            throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);
        }
        try {
            String lockToken = redisLockManager.lock(BattleRedisKeyUtil.lockKey(roomId));
            if (lockToken == null) {
                throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);
            }
            try {
                BattleRoomState state = getRoomState(roomId).orElseThrow(() -> new BattleException(BattleErrorCode.ROOM_NOT_FOUND));
                if (!Objects.equals(state.getHostUserId(), hostUserId)) {
                    throw new BattleException(BattleErrorCode.NOT_PARTICIPANT);
                }
                if (state.getStatus() == BattleStatus.RUNNING) {
                    throw new BattleException(BattleErrorCode.INVALID_STATUS);
                }
                Long guestId = state.getGuestUserId();
                if (guestId != null) {
                    state.setGuestUserId(null);
                    Optional.ofNullable(state.getParticipants()).ifPresent(map -> map.remove(guestId));
                    clearActiveRoom(guestId, roomId);
                    removeMember(roomId, guestId);
                    stringRedisTemplate.opsForSet().add(BattleRedisKeyUtil.kickedKey(roomId), String.valueOf(guestId));
                }
                state.setCountdownStarted(false);
                state.setStatus(BattleStatus.WAITING);
                saveRoom(state);
                broadcastLobby();
                battleMessageService.publishRoomState(state);
                if (guestId != null) {
                    battleMessageService.sendErrorToUser(guestId, BattleErrorCode.KICKED);
                }
                log.info("[battle] matchId={} userId={} action=kick guest={}", state.getMatchId(), hostUserId, guestId);
                return BattleRoomResponse.from(state);
            } finally {
                redisLockManager.unlock(BattleRedisKeyUtil.lockKey(roomId), lockToken);
            }
        } finally {
            redisLockManager.unlock(BattleRedisKeyUtil.userLockKey(hostUserId), userLock);
        }
    }

    public BattleRoomResponse ready(String roomId, Long userId, boolean ready) {
        if (battlePenaltyService.hasPenalty(userId)) {
            throw new BattleException(BattleErrorCode.INVALID_STATUS);
        }
        String userLock = redisLockManager.lock(BattleRedisKeyUtil.userLockKey(userId));
        if (userLock == null) {
            throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);
        }
        try {
            String lockToken = redisLockManager.lock(BattleRedisKeyUtil.lockKey(roomId));
            if (lockToken == null) {
                throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);
            }
            try {
                BattleRoomState state = getRoomState(roomId).orElseThrow(() -> new BattleException(BattleErrorCode.ROOM_NOT_FOUND));
                if (state.getStatus() != BattleStatus.WAITING && !(state.getStatus() == BattleStatus.COUNTDOWN && !ready)) {
                    throw new BattleException(BattleErrorCode.READY_NOT_ALLOWED);
                }
                if (!isParticipant(state, userId)) {
                    throw new BattleException(BattleErrorCode.NOT_PARTICIPANT);
                }

                BattleParticipantState participant = Optional.ofNullable(state.participant(userId))
                        .orElseGet(() -> BattleParticipantState.builder().userId(userId).build());
                participant.setReady(ready);
                state.addOrUpdateParticipant(participant);

                if (!ready) {
                    state.setCountdownStarted(false);
                    if (state.getStatus() == BattleStatus.COUNTDOWN) {
                        state.setStatus(BattleStatus.WAITING);
                        battleSettlementService.refundAll(state);
                    }
                }

                saveRoom(state);
                setActiveRoom(userId, roomId);
                addMember(roomId, userId);
                ensureNicknames(state);
                battleMessageService.publishRoomState(state);

                if (bothReady(state) && !state.isCountdownStarted()) {
                    startCountdown(state);
                }

                log.info("[battle] matchId={} userId={} action=ready state={}", state.getMatchId(), userId, state.getStatus());
                return BattleRoomResponse.from(state);
            } finally {
                redisLockManager.unlock(BattleRedisKeyUtil.lockKey(roomId), lockToken);
            }
        } finally {
            redisLockManager.unlock(BattleRedisKeyUtil.userLockKey(userId), userLock);
        }
    }

    public BattleRoomResponse submit(Long userId, BattleSubmitMessage message) {
        String roomId = message.getRoomId();
        if (battlePenaltyService.hasPenalty(userId)) {
            throw new BattleException(BattleErrorCode.INVALID_STATUS);
        }
        String userLock = redisLockManager.lock(BattleRedisKeyUtil.userLockKey(userId));
        if (userLock == null) {
            throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);
        }
        BattleRoomState state;
        Instant submittedAt = null;
        Long elapsedSeconds = null;
        try {
            String lockToken = redisLockManager.lock(BattleRedisKeyUtil.lockKey(roomId));
            if (lockToken == null) {
                throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);
            }
            try {
                state = getRoomState(roomId).orElseThrow(() -> new BattleException(BattleErrorCode.ROOM_NOT_FOUND));
                if (state.getStatus() != BattleStatus.RUNNING) {
                    throw new BattleException(BattleErrorCode.NOT_RUNNING);
                }
                if (!isParticipant(state, userId)) {
                    throw new BattleException(BattleErrorCode.NOT_PARTICIPANT);
                }

                BattleParticipantState host = state.participant(state.getHostUserId());
                BattleParticipantState guest = state.participant(state.getGuestUserId());
                log.info("[battle] matchId={} action=submit userId={} status={} submittedHost={} submittedGuest={}",
                        state.getMatchId(), userId, state.getStatus(),
                        host != null && host.isFinished(), guest != null && guest.isFinished());

                BattleParticipantState participant = Optional.ofNullable(state.participant(userId))
                        .orElseGet(() -> BattleParticipantState.builder().userId(userId).build());
                Instant now = Instant.now();
                if (participant.getLastSubmittedAt() != null
                        && Duration.between(participant.getLastSubmittedAt(), now).compareTo(SUBMIT_COOLDOWN) < 0) {
                    throw new BattleException(BattleErrorCode.SUBMIT_COOLDOWN);
                }

                participant.setLastSubmittedAt(now);
                if (state.getStartedAt() != null) {
                    participant.setElapsedSeconds(Duration.between(state.getStartedAt(), now).getSeconds());
                    elapsedSeconds = participant.getElapsedSeconds();
                }
                participant.setFinished(true);
                submittedAt = participant.getLastSubmittedAt();
                state.addOrUpdateParticipant(participant);
                saveRoom(state);
                setActiveRoom(userId, roomId);
                addMember(roomId, userId);
            } finally {
                redisLockManager.unlock(BattleRedisKeyUtil.lockKey(roomId), lockToken);
            }
        } finally {
            redisLockManager.unlock(BattleRedisKeyUtil.userLockKey(userId), userLock);
        }

        if (submittedAt == null) {
            submittedAt = Instant.now();
        }
        if (elapsedSeconds == null && state.getStartedAt() != null && submittedAt != null) {
            elapsedSeconds = Duration.between(state.getStartedAt(), submittedAt).getSeconds();
        }

        BattleJudgeResult result;
        try {
            result = battleJudgeService.judge(state.getMatchId(), userId, message);
        } catch (Exception e) {
            result = BattleJudgeResult.rejected("채점 실패: " + e.getMessage());
        }
        BigDecimal baseScore = deriveBaseScore(result);
        updateScore(state, userId, submittedAt, elapsedSeconds, baseScore);

        BattleSubmitResultResponse submitResult = BattleSubmitResultResponse.builder()
                .userId(userId)
                .submittedAt(submittedAt != null ? submittedAt : Instant.now())
                .elapsedSeconds(elapsedSeconds)
                .baseScore(baseScore)
                .timeBonus(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .finalScore(baseScore)
                .message(result != null ? result.getMessage() : null)
                .build();
        battleMessageService.publishSubmitResult(submitResult, state.getRoomId(), state.getMatchId());

        markFinishedIfBothSubmitted(state);
        return BattleRoomResponse.from(getRoomState(roomId).orElse(state));
    }

    public BattleRoomResponse surrender(String roomId, Long userId) {
        String lockToken = redisLockManager.lock(BattleRedisKeyUtil.lockKey(roomId));
        if (lockToken == null) {
            throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);
        }
        try {
            BattleRoomState state = getRoomState(roomId).orElseThrow(() -> new BattleException(BattleErrorCode.ROOM_NOT_FOUND));
            if (state.getStatus() != BattleStatus.RUNNING && state.getStatus() != BattleStatus.COUNTDOWN) {
                throw new BattleException(BattleErrorCode.INVALID_STATUS);
            }
            if (!isParticipant(state, userId)) {
                throw new BattleException(BattleErrorCode.NOT_PARTICIPANT);
            }
            Long winner = Objects.equals(userId, state.getHostUserId()) ? state.getGuestUserId() : state.getHostUserId();
            BattleParticipantState me = state.participant(userId);
            BattleParticipantState opp = winner != null ? state.participant(winner) : null;
            if (me != null) {
                me.setFinished(true);
                me.setFinalScore(BigDecimal.ZERO);
                state.addOrUpdateParticipant(me);
            }
            if (opp != null) {
                opp.setFinished(true);
                opp.setFinalScore(opp.getBaseScore() != null ? opp.getBaseScore() : BigDecimal.ZERO);
                state.addOrUpdateParticipant(opp);
            }
            saveRoom(state);
            finalizeIfReady(roomId, "SURRENDER");
            return BattleRoomResponse.from(getRoomState(roomId).orElse(state));
        } finally {
            redisLockManager.unlock(BattleRedisKeyUtil.lockKey(roomId), lockToken);
        }
    }

    private void markFinishedIfBothSubmitted(BattleRoomState state) {
        finalizeIfReady(state.getRoomId(), "BOTH_SUBMITTED");
    }

    private BigDecimal deriveBaseScore(BattleJudgeResult result) {
        if (result == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (result.isAccepted()) {
            return BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private void updateScore(BattleRoomState state, Long userId, Instant submittedAt, Long elapsedSeconds, BigDecimal baseScore) {
        BattleParticipantState participant = state.participant(userId);
        if (participant == null) return;
        participant.setBaseScore(baseScore);
        participant.setTimeBonus(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        participant.setFinalScore(baseScore);
        if (submittedAt != null) {
            participant.setLastSubmittedAt(submittedAt);
        }
        if (elapsedSeconds != null) {
            participant.setElapsedSeconds(elapsedSeconds);
        }
        participant.setFinished(true);
        state.addOrUpdateParticipant(participant);
        saveRoom(state);
    }

    private void applyTimeBonusAndWinner(BattleRoomState state) {
        BattleParticipantState host = state.participant(state.getHostUserId());
        BattleParticipantState guest = state.participant(state.getGuestUserId());
        if (host == null || guest == null) return;

        Long hostElapsed = host.getElapsedSeconds();
        Long guestElapsed = guest.getElapsedSeconds();
        BigDecimal bonusHost = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal bonusGuest = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (hostElapsed != null && guestElapsed != null && !hostElapsed.equals(guestElapsed)) {
            long diff = Math.abs(hostElapsed - guestElapsed);
            BigDecimal bonus = BigDecimal.valueOf(diff).multiply(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP);
            if (hostElapsed < guestElapsed) {
                bonusHost = bonus;
            } else {
                bonusGuest = bonus;
            }
        }
        if (host.getBaseScore() != null) {
            host.setTimeBonus(bonusHost);
            host.setFinalScore(host.getBaseScore().add(bonusHost));
        }
        if (guest.getBaseScore() != null) {
            guest.setTimeBonus(bonusGuest);
            guest.setFinalScore(guest.getBaseScore().add(bonusGuest));
        }

        BigDecimal hostFinal = host.getFinalScore() != null ? host.getFinalScore() : BigDecimal.ZERO;
        BigDecimal guestFinal = guest.getFinalScore() != null ? guest.getFinalScore() : BigDecimal.ZERO;
        if (hostFinal.compareTo(guestFinal) > 0) {
            state.setWinnerUserId(state.getHostUserId());
        } else if (guestFinal.compareTo(hostFinal) > 0) {
            state.setWinnerUserId(state.getGuestUserId());
        } else if (hostElapsed != null && guestElapsed != null) {
            state.setWinnerUserId(hostElapsed <= guestElapsed ? state.getHostUserId() : state.getGuestUserId());
        } else {
            state.setWinnerUserId(state.getHostUserId());
        }
        state.addOrUpdateParticipant(host);
        state.addOrUpdateParticipant(guest);
    }

    public BattleRoomResponse updateSettings(String roomId, Long userId, BattleRoomUpdateRequest request) {
        String lockToken = redisLockManager.lock(BattleRedisKeyUtil.lockKey(roomId));
        if (lockToken == null) {
            throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);
        }
        try {
            BattleRoomState state = getRoomState(roomId).orElseThrow(() -> new BattleException(BattleErrorCode.ROOM_NOT_FOUND));
            if (!Objects.equals(state.getHostUserId(), userId)) {
                throw new BattleException(BattleErrorCode.NOT_PARTICIPANT);
            }
            if (state.getStatus() != BattleStatus.WAITING && state.getStatus() != BattleStatus.COUNTDOWN) {
                throw new BattleException(BattleErrorCode.INVALID_STATUS);
            }
            // ?쒕ぉ
            if (request.getTitle() != null) {
                state.setTitle(request.getTitle());
            }
            // 臾몄젣/?몄뼱
            if (request.getAlgoProblemId() != null) {
                state.setAlgoProblemId(request.getAlgoProblemId());
            }
            if (request.getLanguageId() != null) {
                state.setLanguageId(request.getLanguageId());
            }
            // ?덈꺼 紐⑤뱶
            if (request.getLevelMode() != null && !request.getLevelMode().isBlank()) {
                state.setLevelMode(request.getLevelMode());
            }
            // 踰좏똿
            if (request.getBetAmount() != null) {
                battleValidator.validateBetAmount(request.getBetAmount());
                state.setBetAmount(request.getBetAmount());
            }
            if (request.getMaxDurationMinutes() != null) {
                int minutes = battleDurationPolicy.effectiveMinutes(request.getMaxDurationMinutes(), findProblemDifficulty(state.getAlgoProblemId()));
                state.setMaxDurationMinutes(minutes);
                battleMatchService.updateMaxDuration(state.getMatchId(), minutes);
            }
            // 鍮꾨?諛?鍮꾨?踰덊샇
            if (request.getIsPrivate() != null) {
                if (Boolean.TRUE.equals(request.getIsPrivate())) {
                    if (request.getNewPassword() == null || !request.getNewPassword().matches("^\\d{4}$")) {
                        throw new BattleException(BattleErrorCode.INVALID_STATUS);
                    }
                    state.setPrivate(true);
                    state.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
                } else {
                    state.setPrivate(false);
                    state.setPasswordHash(null);
                }
            } else if (request.getNewPassword() != null) {
                if (!request.getNewPassword().matches("^\\d{4}$")) {
                    throw new BattleException(BattleErrorCode.INVALID_STATUS);
                }
                state.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
                state.setPrivate(true);
            }
            saveRoom(state);
            broadcastLobby();
            battleMessageService.publishRoomState(state);
            return BattleRoomResponse.from(state);
        } finally {
            redisLockManager.unlock(BattleRedisKeyUtil.lockKey(roomId), lockToken);
        }
    }

    public BattleRoomResponse finishWithWinner(String roomId, Long winnerUserId) {
        return finishWithReason(roomId, winnerUserId, "ACCEPTED");
    }

    private BattleRoomResponse finishWithReason(String roomId, Long winnerUserId, String reason) {
        String lockToken = redisLockManager.lock(BattleRedisKeyUtil.lockKey(roomId));
        if (lockToken == null) {
            throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);
        }
        try {
            BattleRoomState state = getRoomState(roomId).orElseThrow(() -> new BattleException(BattleErrorCode.ROOM_NOT_FOUND));
            if (state.getStatus() == BattleStatus.FINISHED) {
                cleanupRoomKeys(state.getRoomId(), participantIds(state));
                return BattleRoomResponse.from(state);
            }
            if (!isParticipant(state, winnerUserId)) {
                throw new BattleException(BattleErrorCode.NOT_PARTICIPANT);
            }
            Long loserUserId = Objects.equals(winnerUserId, state.getHostUserId())
                    ? state.getGuestUserId()
                    : state.getHostUserId();

            state.setWinnerUserId(winnerUserId);
            state.setWinReason(reason);
            state.setFinishedAt(Instant.now());
            state.setStatus(BattleStatus.FINISHED);
            saveRoom(state);
            removeRoomFromLobby(state.getRoomId());
            cancelTimeoutTask(state.getRoomId());

            battleSettlementService.settle(state.getMatchId(), winnerUserId, loserUserId, state.getBetAmount());
            battleMatchService.finishMatch(state.getMatchId(), winnerUserId, reason);
            battleMessageService.publishFinish(state);
            broadcastLobby();

            if ("DISCONNECT".equalsIgnoreCase(reason)) {
                battlePenaltyService.recordDisconnectLoss(loserUserId);
                battlePenaltyService.recordNormalFinish(winnerUserId);
            } else {
                battlePenaltyService.recordNormalFinish(winnerUserId);
                battlePenaltyService.recordNormalFinish(loserUserId);
            }

            cleanupRoomKeys(state.getRoomId(), participantIds(state));
            log.info("[battle] matchId={} userId={} action=finish reason={} state={}", state.getMatchId(), winnerUserId, reason, state.getStatus());
            return BattleRoomResponse.from(state);
        } finally {
            redisLockManager.unlock(BattleRedisKeyUtil.lockKey(roomId), lockToken);
        }
    }

    private void startCountdown(BattleRoomState state) {
        BigDecimal bet = state.getBetAmount();
        if (bet == null || bet.compareTo(BigDecimal.ZERO) < 0) {
            bet = BigDecimal.ZERO;
        }
        if (bet.compareTo(BigDecimal.ZERO) > 0) {
            try {
                holdUserBet(state, state.getHostUserId(), bet);
                if (state.getGuestUserId() != null) {
                    holdUserBet(state, state.getGuestUserId(), bet);
                }
                battleMatchService.updateSettlementStatus(state.getMatchId(), kr.or.kosa.backend.battle.domain.BattleSettlementStatus.HELD);
            } catch (BattleException e) {
                return;
            } catch (Exception e) {
                log.error("[battle] matchId={} action=hold error={}", state.getMatchId(), e.getMessage(), e);
                handleHoldFailure(state, null, new BattleException(BattleErrorCode.HOLD_UNKNOWN));
                return;
            }
        }
        state.setStatus(BattleStatus.COUNTDOWN);
        state.setCountdownStarted(true);
        saveRoom(state);
        battleMatchService.markCountdown(state.getMatchId());
        battleMessageService.publishRoomState(state);
        broadcastLobby();

        for (int i = COUNTDOWN_SECONDS; i >= 1; i--) {
            int secondsLeft = i;
            battleTaskScheduler.schedule(
                    () -> getRoomState(state.getRoomId())
                            .filter(current -> current.getStatus() == BattleStatus.COUNTDOWN)
                            .ifPresent(current -> battleMessageService.publishCountdown(current, secondsLeft)),
                    Instant.now().plusSeconds(COUNTDOWN_SECONDS - i));
        }
        battleTaskScheduler.schedule(() -> startMatch(state.getRoomId()), Instant.now().plusSeconds(COUNTDOWN_SECONDS));
    }

    private void holdUserBet(BattleRoomState state, Long userId, BigDecimal betAmount) {
        try {
            battlePointPort.holdBet(state.getMatchId(), userId, betAmount);
        } catch (BattleException ex) {
            handleHoldFailure(state, userId, ex);
            throw ex;
        } catch (Exception ex) {
            handleHoldFailure(state, userId, new BattleException(BattleErrorCode.HOLD_UNKNOWN));
            throw new BattleException(BattleErrorCode.HOLD_UNKNOWN);
        }
    }

    private void handleHoldFailure(BattleRoomState state, Long offenderId, BattleException ex) {
        state.setCountdownStarted(false);
        state.setStatus(BattleStatus.WAITING);
        if (state.getParticipants() != null) {
            state.getParticipants().values().forEach(p -> p.setReady(false));
        }
        battleSettlementService.refundAll(state);
        saveRoom(state);
        battleMessageService.publishRoomState(state);
        broadcastLobby();

        String nickname = offenderId != null ? fetchNickname(offenderId) : "참가자";
        String message;
        if (ex.getErrorCode() == BattleErrorCode.POINT_ACCOUNT_MISSING) {
            message = nickname + "님의 포인트 정보가 없습니다. 다시 로그인 후 시도해 주세요.";
        } else if (ex.getErrorCode() == BattleErrorCode.INSUFFICIENT_POINTS) {
            message = nickname + "님의 포인트가 부족합니다.";
        } else {
            message = ex.getErrorCode().getMessage();
        }

        Long hostId = state.getHostUserId();
        Long guestId = state.getGuestUserId();
        if (hostId != null) {
            battleMessageService.sendErrorToUser(hostId, ex.getErrorCode(), message);
        }
        if (guestId != null) {
            battleMessageService.sendErrorToUser(guestId, ex.getErrorCode(), message);
        }
    }

    private void startMatch(String roomId) {
        String lockToken = redisLockManager.lock(BattleRedisKeyUtil.lockKey(roomId));
        if (lockToken == null) {
            return;
        }
        try {
            BattleRoomState state = getRoomState(roomId).orElse(null);
            if (state == null || state.getStatus() != BattleStatus.COUNTDOWN) {
                return;
            }
            if (!bothReady(state)) {
                state.setCountdownStarted(false);
                state.setStatus(BattleStatus.WAITING);
                saveRoom(state);
                battleSettlementService.refundAll(state);
                battleMessageService.publishRoomState(state);
                broadcastLobby();
                return;
            }
            state.setStatus(BattleStatus.RUNNING);
            state.setStartedAt(Instant.now());
            saveRoom(state);
            battleMatchService.markRunning(state.getMatchId());
            removeRoomFromLobby(state.getRoomId());
            scheduleTimeout(state);
            battleMessageService.publishStart(state);
            battleMessageService.publishRoomState(state);
            broadcastLobby();
            log.info("[battle] matchId={} action=start state={}", state.getMatchId(), state.getStatus());
        } finally {
            redisLockManager.unlock(BattleRedisKeyUtil.lockKey(roomId), lockToken);
        }
    }

    private void cancelRoom(BattleRoomState state) {
        state.setStatus(BattleStatus.CANCELED);
        state.setFinishedAt(Instant.now());
        saveRoom(state);
        removeRoomFromLobby(state.getRoomId());
        if (state.isCountdownStarted()) {
            battleSettlementService.refundAll(state);
        }
        battleMatchService.markCanceled(state.getMatchId());
        cancelTimeoutTask(state.getRoomId());
        battleMessageService.publishFinish(state);
        broadcastLobby();
        cleanupRoomKeys(state.getRoomId(), participantIds(state));
        log.info("[battle] matchId={} action=cancel state={}", state.getMatchId(), state.getStatus());
    }

    private boolean bothReady(BattleRoomState state) {
        if (state.getHostUserId() == null || state.getGuestUserId() == null) {
            return false;
        }
        BattleParticipantState host = state.participant(state.getHostUserId());
        BattleParticipantState guest = state.participant(state.getGuestUserId());
        return host != null && host.isReady() && guest != null && guest.isReady();
    }

    private boolean isParticipant(BattleRoomState state, Long userId) {
        return Objects.equals(state.getHostUserId(), userId) || Objects.equals(state.getGuestUserId(), userId);
    }

    private Optional<BattleRoomState> getRoomState(String roomId) {
        Object value = redisTemplate.opsForValue().get(BattleRedisKeyUtil.roomKey(roomId));
        if (value instanceof BattleRoomState state) {
            return Optional.of(state);
        }
        return Optional.empty();
    }

    private void saveRoom(BattleRoomState state) {
        redisTemplate.opsForValue().set(BattleRedisKeyUtil.roomKey(state.getRoomId()), state);
    }

    private void addRoomToLobby(String roomId) {
        stringRedisTemplate.opsForSet().add(BattleRedisKeyUtil.lobbyKey(), roomId);
    }

    private void removeRoomFromLobby(String roomId) {
        stringRedisTemplate.opsForSet().remove(BattleRedisKeyUtil.lobbyKey(), roomId);
    }

    private void broadcastLobby() {
        List<BattleRoomResponse> rooms = listRooms();
        battleMessageService.publishRoomList(rooms);
    }

    private void scheduleTimeout(BattleRoomState state) {
        cancelTimeoutTask(state.getRoomId());
        Integer maxDuration = state.getMaxDurationMinutes();
        if (maxDuration == null || maxDuration <= 0) {
            maxDuration = battleDurationPolicy.defaultMinutes(null);
        }
        ScheduledFuture<?> future = battleTaskScheduler.schedule(
                () -> finishByTimeout(state.getRoomId()),
                Instant.now().plusSeconds(maxDuration * 60L));
        timeoutTasks.put(state.getRoomId(), future);
    }

    private void cancelTimeoutTask(String roomId) {
        Optional.ofNullable(timeoutTasks.remove(roomId)).ifPresent(f -> f.cancel(false));
    }

    private void finishByTimeout(String roomId) {
        String lockToken = redisLockManager.lock(BattleRedisKeyUtil.lockKey(roomId));
        if (lockToken == null) {
            return;
        }
        try {
            BattleRoomState state = getRoomState(roomId).orElse(null);
            if (state == null || state.getStatus() != BattleStatus.RUNNING) {
                return;
            }
            long timeoutElapsed = 0L;
            if (state.getStartedAt() != null && state.getMaxDurationMinutes() != null) {
                timeoutElapsed = state.getMaxDurationMinutes() * 60L;
            }
            BattleParticipantState host = state.participant(state.getHostUserId());
            BattleParticipantState guest = state.participant(state.getGuestUserId());
            if (host != null && !host.isFinished()) {
                host.setFinished(true);
                host.setElapsedSeconds(timeoutElapsed);
                state.addOrUpdateParticipant(host);
            }
            if (guest != null && !guest.isFinished()) {
                guest.setFinished(true);
                guest.setElapsedSeconds(timeoutElapsed);
                state.addOrUpdateParticipant(guest);
            }
            saveRoom(state);
            battleSettlementService.refundAll(state);
            log.info("[battle] matchId={} action=timeout submittedHost={} submittedGuest={}",
                    state.getMatchId(),
                    host != null && host.isFinished(),
                    guest != null && guest.isFinished());
        } finally {
            redisLockManager.unlock(BattleRedisKeyUtil.lockKey(roomId), lockToken);
        }
        finalizeIfReady(roomId, "TIMEOUT");
    }

    private void cancelPostGameTask(String roomId) {
        Optional.ofNullable(postGameResetTasks.remove(roomId)).ifPresent(f -> f.cancel(false));
    }

    private boolean allSubmitted(BattleRoomState state) {
        if (state == null || state.getHostUserId() == null || state.getGuestUserId() == null) {
            return false;
        }
        BattleParticipantState host = state.participant(state.getHostUserId());
        BattleParticipantState guest = state.participant(state.getGuestUserId());
        return host != null && guest != null && host.isFinished() && guest.isFinished();
    }

    private void finalizeIfReady(String roomId, String reason) {
        String lockToken = redisLockManager.lock(BattleRedisKeyUtil.lockKey(roomId));
        if (lockToken == null) {
            return;
        }
        try {
            BattleRoomState state = getRoomState(roomId).orElse(null);
            if (state == null) return;
            if (state.getStatus() == BattleStatus.FINISHED) return;
            if (!allSubmitted(state)) return;

            state.setStatus(BattleStatus.FINISHED);
            state.setFinishedAt(Instant.now());
            state.setWinReason(reason);
            applyTimeBonusAndWinner(state);
            ensureNicknames(state);
            state.setPostGameUntil(Instant.now().plus(POSTGAME_LOCK_DURATION));
            saveRoom(state);
            removeRoomFromLobby(state.getRoomId());
            cancelTimeoutTask(state.getRoomId());
            battleMatchService.finishMatch(state.getMatchId(), state.getWinnerUserId(), reason);
            battleMessageService.publishFinish(state);
            broadcastLobby();
            schedulePostGameReset(state);
            log.info("[battle] matchId={} action=finalize reason={} winner={}", state.getMatchId(), reason, state.getWinnerUserId());
        } finally {
            redisLockManager.unlock(BattleRedisKeyUtil.lockKey(roomId), lockToken);
        }
    }

    private void schedulePostGameReset(BattleRoomState state) {
        cancelPostGameTask(state.getRoomId());
        ScheduledFuture<?> future = battleTaskScheduler.schedule(
                () -> resetRoomToWaiting(state.getRoomId()),
                Instant.now().plus(POSTGAME_LOCK_DURATION));
        postGameResetTasks.put(state.getRoomId(), future);
    }

    private void resetRoomToWaiting(String roomId) {
        String lockToken = redisLockManager.lock(BattleRedisKeyUtil.lockKey(roomId));
        if (lockToken == null) {
            return;
        }
        try {
            BattleRoomState state = getRoomState(roomId).orElse(null);
            if (state == null) return;
            state.setStatus(BattleStatus.WAITING);
            state.setCountdownStarted(false);
            state.setStartedAt(null);
            state.setFinishedAt(null);
            state.setPostGameUntil(null);
            state.setWinnerUserId(null);
            state.setWinReason(null);
            Optional.ofNullable(state.getParticipants()).ifPresent(map -> map.values().forEach(p -> {
                p.setReady(false);
                p.setFinished(false);
                p.setElapsedSeconds(null);
                p.setLastSubmittedAt(null);
                p.setBaseScore(null);
                p.setTimeBonus(null);
                p.setFinalScore(null);
            }));
            saveRoom(state);
            addRoomToLobby(roomId);
            broadcastLobby();
            battleMessageService.publishRoomState(state);
        } finally {
            redisLockManager.unlock(BattleRedisKeyUtil.lockKey(roomId), lockToken);
            cancelPostGameTask(roomId);
        }
    }

    private void startDisconnectGrace(BattleRoomState state, Long userId) {
        Long opponent = Objects.equals(userId, state.getHostUserId()) ? state.getGuestUserId() : state.getHostUserId();
        if (opponent == null) {
            cancelRoom(state);
            return;
        }
        String key = graceKey(state.getRoomId(), userId);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            return; // ?대? ?좎삁 ??대㉧媛 ?덉쑝硫?以묐났 ?깅줉?섏? ?딆쓬
        }
        stringRedisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(15));
        ScheduledFuture<?> future = battleTaskScheduler.schedule(
                () -> finishDueToDisconnect(state.getRoomId(), opponent, userId),
                Instant.now().plusSeconds(15));
        disconnectTasks.put(key, future);
        log.info("[battle] matchId={} userId={} action=disconnect-grace", state.getMatchId(), userId);
    }

    private void finishDueToDisconnect(String roomId, Long winnerUserId, Long loserUserId) {
        String key = graceKey(roomId, loserUserId);
        Boolean exists = stringRedisTemplate.hasKey(key);
        if (!Boolean.TRUE.equals(exists)) {
            return;
        }
        stringRedisTemplate.delete(key);
        disconnectTasks.remove(key);
        finishWithReason(roomId, winnerUserId, "DISCONNECT");
    }

    private void cancelDisconnectGrace(String roomId, Long userId) {
        String key = graceKey(roomId, userId);
        stringRedisTemplate.delete(key);
        Optional.ofNullable(disconnectTasks.remove(key)).ifPresent(f -> f.cancel(false));
    }

    private String graceKey(String roomId, Long userId) {
        return "battle:grace:" + roomId + ":" + userId;
    }

    private String findProblemDifficulty(Long algoProblemId) {
        if (algoProblemId == null) {
            return null;
        }
        var problem = algorithmProblemMapper.selectProblemById(algoProblemId);
        if (problem == null || problem.getAlgoProblemDifficulty() == null) {
            return null;
        }
        return problem.getAlgoProblemDifficulty().name();
    }

    private Set<Long> participantIds(BattleRoomState state) {
        if (state == null || state.getParticipants() == null) {
            return Set.of();
        }
        return state.getParticipants().values().stream()
                .map(BattleParticipantState::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private boolean isActive(BattleRoomState state) {
        return state != null && (state.getStatus() == BattleStatus.WAITING
                || state.getStatus() == BattleStatus.COUNTDOWN
                || state.getStatus() == BattleStatus.RUNNING);
    }

    private void setActiveRoom(Long userId, String roomId) {
        if (userId == null || roomId == null) return;
        stringRedisTemplate.opsForValue().set(BattleRedisKeyUtil.activeRoomKey(userId), roomId);
    }

    private void clearActiveRoom(Long userId, String roomId) {
        if (userId == null) return;
        String key = BattleRedisKeyUtil.activeRoomKey(userId);
        String current = stringRedisTemplate.opsForValue().get(key);
        if (roomId == null || Objects.equals(roomId, current)) {
            stringRedisTemplate.delete(key);
        }
    }

    private void addMember(String roomId, Long userId) {
        if (roomId == null || userId == null) return;
        stringRedisTemplate.opsForSet().add(BattleRedisKeyUtil.membersKey(roomId), String.valueOf(userId));
    }

    private void removeMember(String roomId, Long userId) {
        if (roomId == null || userId == null) return;
        stringRedisTemplate.opsForSet().remove(BattleRedisKeyUtil.membersKey(roomId), String.valueOf(userId));
    }

    private void cleanupRoomKeys(String roomId, Set<Long> userIds) {
        try {
            if (!CollectionUtils.isEmpty(userIds)) {
                userIds.stream().filter(Objects::nonNull).forEach(uid -> clearActiveRoom(uid, roomId));
            }
            if (roomId != null) {
                redisTemplate.delete(BattleRedisKeyUtil.roomKey(roomId));
                stringRedisTemplate.delete(BattleRedisKeyUtil.membersKey(roomId));
                removeRoomFromLobby(roomId);
                stringRedisTemplate.delete(BattleRedisKeyUtil.kickedKey(roomId));
            }
        } catch (Exception e) {
            log.warn("[battle] roomId={} action=cleanup error={}", roomId, e.getMessage());
        }
    }

    private void cleanupActiveMapping(Long userId, String roomId, BattleRoomState state) {
        clearActiveRoom(userId, roomId);
        removeMember(roomId, userId);
        if (state != null && !isActive(state)) {
            removeRoomFromLobby(roomId);
        }
    }

    public Optional<BattleRoomResponse> findMyActiveRoom(Long userId) {
        if (userId == null) return Optional.empty();
        String roomId = stringRedisTemplate.opsForValue().get(BattleRedisKeyUtil.activeRoomKey(userId));
        if (roomId == null) return Optional.empty();
        Optional<BattleRoomState> stateOpt = getRoomState(roomId);
        if (stateOpt.isEmpty() || !isActive(stateOpt.get())) {
            cleanupRoomKeys(roomId, Set.of(userId));
            return Optional.empty();
        }
        ensureNicknames(stateOpt.get());
        return Optional.of(BattleRoomResponse.from(stateOpt.get()));
    }

    private String fetchNickname(Long userId) {
        if (userId == null) {
            return null;
        }
        if (battleUserPort != null) {
            return battleUserPort.findProfile(userId)
                    .map(BattleUserProfile::getNickname)
                    .orElse("사용자#" + userId);
        }
        return "사용자#" + userId;
    }

    private BigDecimal fetchPointBalance(Long userId) {
        if (userId == null) {
            return BigDecimal.ZERO;
        }
        try {
            return battlePointPort.getBalance(userId);
        } catch (Exception e) {
            log.warn("[battle] userId={} action=fetchPointBalance error={}", userId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private void enforcePasswordRateLimit(String roomId, Long userId) {
        String lockKey = BattleRedisKeyUtil.passwordLockKey(roomId, userId);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey))) {
            throw new BattleException(BattleErrorCode.INVALID_STATUS);
        }
        String key = BattleRedisKeyUtil.passwordAttemptKey(roomId, userId);
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, PASSWORD_ATTEMPT_WINDOW);
        }
        if (count != null && count > PASSWORD_ATTEMPT_LIMIT) {
            stringRedisTemplate.opsForValue().set(lockKey, "1", PASSWORD_LOCK_DURATION);
            throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);
        }
    }

    private void registerPasswordFailure(String roomId, Long userId) {
        // already counted in enforcePasswordRateLimit
    }

    private void clearPasswordAttempts(String roomId, Long userId) {
        stringRedisTemplate.delete(BattleRedisKeyUtil.passwordAttemptKey(roomId, userId));
        stringRedisTemplate.delete(BattleRedisKeyUtil.passwordLockKey(roomId, userId));
    }

    private void randomDelay() {
        try {
            Thread.sleep(200 + (long) (Math.random() * 200));
        } catch (InterruptedException ignored) {
        }
    }
}




