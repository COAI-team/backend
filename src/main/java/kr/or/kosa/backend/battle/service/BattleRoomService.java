package kr.or.kosa.backend.battle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant; // 스케줄러 경계(예약)에서만 필요
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import kr.or.kosa.backend.algorithm.mapper.AlgorithmProblemMapper;
import kr.or.kosa.backend.battle.domain.BattleParticipantState;
import kr.or.kosa.backend.battle.domain.BattleRoomState;
import kr.or.kosa.backend.battle.domain.BattleSettlementStatus;
import kr.or.kosa.backend.battle.domain.BattleStatus;
import kr.or.kosa.backend.battle.dto.BattleRoomCreateRequest;
import kr.or.kosa.backend.battle.dto.BattleRoomResponse;
import kr.or.kosa.backend.battle.dto.BattleRoomUpdateRequest;
import kr.or.kosa.backend.battle.dto.BattleSubmitMessage;
import kr.or.kosa.backend.battle.dto.BattleSubmitResultResponse;
import kr.or.kosa.backend.battle.exception.BattleErrorCode;
import kr.or.kosa.backend.battle.exception.BattleException;
import kr.or.kosa.backend.battle.port.BattlePointPort;
import kr.or.kosa.backend.battle.port.BattleUserPort;
import kr.or.kosa.backend.battle.port.dto.BattleJudgeResult;
import kr.or.kosa.backend.battle.port.dto.BattleUserProfile;
import kr.or.kosa.backend.battle.util.BattleDurationPolicy;
import kr.or.kosa.backend.battle.util.BattleRedisKeyUtil;
import kr.or.kosa.backend.battle.util.BattleTime;
import kr.or.kosa.backend.battle.util.BattleValidator;
import kr.or.kosa.backend.battle.util.RedisLockManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
@RequiredArgsConstructor
public class BattleRoomService {
    private static final Logger log = LoggerFactory.getLogger(BattleRoomService.class);

    private static final int COUNTDOWN_SECONDS = 5;
    private static final Duration SUBMIT_COOLDOWN = Duration.ofSeconds(2);
    private static final Duration POSTGAME_LOCK_DURATION = Duration.ofSeconds(30);
    private static final BigDecimal TIME_BONUS_PER_SECOND = new BigDecimal("0.01");
    private static final BigDecimal MAX_SCORE = new BigDecimal("100.00");
    private static final int JUDGE_ERROR_LIMIT = 3;
    private static final String JUDGE_RETRY_MESSAGE = "\u0041\u0049 \uC624\uB958! \uCC44\uC810\uC744 \uB2E4\uC2DC \uB20C\uB7EC\uC8FC\uC138\uC694.";

    private static final int PASSWORD_ATTEMPT_LIMIT = 5;
    private static final Duration PASSWORD_ATTEMPT_WINDOW = Duration.ofMinutes(1);
    private static final Duration PASSWORD_LOCK_DURATION = Duration.ofMinutes(5);

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
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private BattleUserPort battleUserPort;

    private final PasswordEncoder passwordEncoder;

    @Qualifier("battleTaskScheduler")
    private final TaskScheduler battleTaskScheduler;

    @org.springframework.beans.factory.annotation.Autowired
    @Qualifier("battleRedisTemplate")
    private RedisTemplate<String, Object> redisTemplate;

    private final ConcurrentHashMap<String, ScheduledFuture<?>> timeoutTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> disconnectTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> postGameTasks = new ConcurrentHashMap<>();

    /* ------------------------------------------------------------
     * Core helpers
     * ------------------------------------------------------------ */

    private void ensureNicknames(BattleRoomState state) {
        if (state == null) return;

        Set<Long> ids = new java.util.HashSet<>();
        if (state.getHostUserId() != null) ids.add(state.getHostUserId());
        if (state.getGuestUserId() != null) ids.add(state.getGuestUserId());
        if (state.getParticipants() != null) ids.addAll(state.getParticipants().keySet());

        for (Long id : ids) {
            if (id == null) continue;

            BattleParticipantState participant = state.participant(id);
            if (participant == null) {
                participant = BattleParticipantState.builder()
                        .userId(id)
                        .ready(false)
                        .finished(false)
                        .build();
            }

            if (participant.getNickname() == null || participant.getNickname().isBlank()) {
                participant.setNickname(fetchNickname(id));
            }
            if (participant.getGrade() == null) {
                participant.setGrade(fetchUserGrade(id));
            }
            participant.setPointBalance(fetchPointBalance(id));
            state.addOrUpdateParticipant(participant);
        }
    }

    private void syncParticipantsWithMembers(BattleRoomState state, Set<String> members) {
        if (state == null || members == null) return;
        Long beforeHost = state.getHostUserId();
        Long beforeGuest = state.getGuestUserId();
        if (state.getParticipants() != null) {
            state.getParticipants().values().removeIf(participant ->
                    participant == null
                            || participant.getUserId() == null
                            || !members.contains(String.valueOf(participant.getUserId()))
            );
        }
        boolean hostIn = state.getHostUserId() != null && members.contains(String.valueOf(state.getHostUserId()));
        boolean guestIn = state.getGuestUserId() != null && members.contains(String.valueOf(state.getGuestUserId()));

        if (!hostIn && guestIn) {
            state.setHostUserId(state.getGuestUserId());
            state.setGuestUserId(null);
        } else if (hostIn && !guestIn) {
            state.setGuestUserId(null);
        } else if (!hostIn && !guestIn) {
            state.setHostUserId(null);
            state.setGuestUserId(null);
        }

        if (state.getHostUserId() == null && !members.isEmpty()) {
            try {
                state.setHostUserId(Long.valueOf(members.iterator().next()));
            } catch (NumberFormatException ignored) {
                // ignore invalid member id
            }
        }

        if (!Objects.equals(beforeHost, state.getHostUserId())
                || !Objects.equals(beforeGuest, state.getGuestUserId())) {
            battleMatchService.updateParticipants(state.getMatchId(), state.getHostUserId(), state.getGuestUserId());
        }
    }

    /**
     * "방 삭제/버튼 먹통"류 방어:
     * - members/activeRoom/lobby 상태가 꼬인 경우 재보장
     * - FINISHED(포스트게임) 동안에도 참가자 매핑 유지
     */
    private void ensureActiveAndMembers(BattleRoomState state) {
        if (state == null || state.getRoomId() == null) return;

        LocalDateTime now = BattleTime.nowKst();
        boolean finishedPostGame =
                state.getStatus() == BattleStatus.FINISHED
                        && state.getPostGameUntil() != null
                        && state.getPostGameUntil().isAfter(now);
        boolean keepMappings = isActive(state) || finishedPostGame;

        if (keepMappings) {
            if (state.getHostUserId() != null) {
                boolean keepHost = !finishedPostGame || Boolean.TRUE.equals(
                        stringRedisTemplate.opsForSet().isMember(
                                BattleRedisKeyUtil.membersKey(state.getRoomId()),
                                String.valueOf(state.getHostUserId())
                        )
                );
                if (keepHost) {
                    setActiveRoom(state.getHostUserId(), state.getRoomId());
                    addMember(state.getRoomId(), state.getHostUserId());
                }
            }
            if (state.getGuestUserId() != null) {
                boolean keepGuest = !finishedPostGame || Boolean.TRUE.equals(
                        stringRedisTemplate.opsForSet().isMember(
                                BattleRedisKeyUtil.membersKey(state.getRoomId()),
                                String.valueOf(state.getGuestUserId())
                        )
                );
                if (keepGuest) {
                    setActiveRoom(state.getGuestUserId(), state.getRoomId());
                    addMember(state.getRoomId(), state.getGuestUserId());
                }
            }
        }

        if (state.getStatus() == BattleStatus.WAITING || state.getStatus() == BattleStatus.COUNTDOWN) {
            addRoomToLobby(state.getRoomId());
        } else {
            removeRoomFromLobby(state.getRoomId());
        }
    }

    private boolean isParticipant(BattleRoomState state, Long userId) {
        return state != null && (Objects.equals(state.getHostUserId(), userId) || Objects.equals(state.getGuestUserId(), userId));
    }

    private boolean isActive(BattleRoomState state) {
        return state != null && (
                state.getStatus() == BattleStatus.WAITING
                        || state.getStatus() == BattleStatus.COUNTDOWN
                        || state.getStatus() == BattleStatus.RUNNING
        );
    }

    private Optional<BattleRoomState> getRoomState(String roomId) {
        Object value = redisTemplate.opsForValue().get(BattleRedisKeyUtil.roomKey(roomId));
        if (value instanceof BattleRoomState state) {
            return Optional.of(state);
        }
        if (value instanceof java.util.Map<?, ?> map) {
            try {
                BattleRoomState state = objectMapper.convertValue(map, BattleRoomState.class);
                return Optional.ofNullable(state);
            } catch (IllegalArgumentException e) {
                log.warn("[battle] roomId={} action=deserialize-state error={}", roomId, e.getMessage());
            }
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

    private void setActiveRoom(Long userId, String roomId) {
        if (userId == null || roomId == null) return;
        stringRedisTemplate.opsForValue().set(BattleRedisKeyUtil.activeRoomKey(userId), roomId);
    }

    private void setMatchRoomMapping(String matchId, String roomId) {
        if (matchId == null || roomId == null) return;
        stringRedisTemplate.opsForValue().set(BattleRedisKeyUtil.matchRoomKey(matchId), roomId);
    }

    private void clearMatchRoomMapping(String matchId) {
        if (matchId == null) return;
        stringRedisTemplate.delete(BattleRedisKeyUtil.matchRoomKey(matchId));
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

    private Set<Long> participantIds(BattleRoomState state) {
        if (state == null || state.getParticipants() == null) return Set.of();
        return state.getParticipants().values().stream()
                .map(BattleParticipantState::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private void cleanupRoomKeys(String roomId, Set<Long> userIds) {
        try {
            if (!CollectionUtils.isEmpty(userIds)) {
                userIds.stream().filter(Objects::nonNull).forEach(uid -> clearActiveRoom(uid, roomId));
            }
            if (roomId != null) {
                getRoomState(roomId).ifPresent(state -> clearMatchRoomMapping(state.getMatchId()));
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

    private String fetchNickname(Long userId) {
        if (userId == null) return null;
        if (battleUserPort != null) {
            return battleUserPort.findProfile(userId)
                    .map(BattleUserProfile::getNickname)
                    .orElse("\uC0AC\uC6A9\uC790#" + userId);
        }
        return "\uC0AC\uC6A9\uC790#" + userId;
    }

    private BigDecimal fetchPointBalance(Long userId) {
        if (userId == null) return BigDecimal.ZERO;
        try {
            return battlePointPort.getBalance(userId);
        } catch (Exception e) {
            log.warn("[battle] userId={} action=fetchPointBalance error={}", userId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private String findProblemDifficulty(Long algoProblemId) {
        if (algoProblemId == null) return null;
        var problem = algorithmProblemMapper.selectProblemById(algoProblemId);
        if (problem == null || problem.getAlgoProblemDifficulty() == null) return null;
        return problem.getAlgoProblemDifficulty().name();
    }

    private Long normalizeProblemId(Long algoProblemId) {
        if (algoProblemId == null) return null;
        return algoProblemId > 0 ? algoProblemId : null;
    }

    private Long selectRandomProblemId() {
        int total = algorithmProblemMapper.countAllProblems();
        if (total <= 0) {
            throw new BattleException(BattleErrorCode.PROBLEM_NOT_FOUND);
        }
        int offset = ThreadLocalRandom.current().nextInt(total);
        var list = algorithmProblemMapper.selectProblems(offset, 1);
        if (list == null || list.isEmpty() || list.get(0).getAlgoProblemId() == null) {
            throw new BattleException(BattleErrorCode.PROBLEM_NOT_FOUND);
        }
        return list.get(0).getAlgoProblemId();
    }

    private Long applyProblemSelection(BattleRoomState state, Long requestedProblemId) {
        Long normalized = normalizeProblemId(requestedProblemId);
        if (normalized == null) {
            state.setRandomProblem(true);
            Long randomId = selectRandomProblemId();
            state.setAlgoProblemId(randomId);
            return randomId;
        }
        state.setRandomProblem(false);
        state.setAlgoProblemId(normalized);
        return normalized;
    }

    private String normalizeLevelMode(String levelMode) {
        if (levelMode == null || levelMode.isBlank()) return "ANY";
        String upper = levelMode.trim().toUpperCase();
        if (upper.contains("SAME")) return "SAME_LINE_ONLY";
        if (upper.contains("ANY") || upper.contains("NONE") || upper.contains("UNLIMITED")) return "ANY";
        return upper.length() > 10 ? "ANY" : upper;
    }

    private Integer fetchUserGrade(Long userId) {
        if (userId == null || battleUserPort == null) return null;
        return battleUserPort.findProfile(userId)
                .map(BattleUserProfile::getGrade)
                .orElse(null);
    }

    private boolean isSameGradeMode(BattleRoomState state) {
        if (state == null) return false;
        String mode = normalizeLevelMode(state.getLevelMode());
        return "SAME_LINE_ONLY".equalsIgnoreCase(mode);
    }

    private boolean isGradeMismatch(Long hostUserId, Long guestUserId) {
        Integer hostGrade = fetchUserGrade(hostUserId);
        Integer guestGrade = fetchUserGrade(guestUserId);
        if (hostGrade == null || guestGrade == null) return true;
        return !hostGrade.equals(guestGrade);
    }

    /* ------------------------------------------------------------
     * Public APIs (Controller / WS)
     * ------------------------------------------------------------ */

    public BattleRoomResponse createRoom(Long userId, BattleRoomCreateRequest request) {
        if (battlePenaltyService.hasPenalty(userId)) {
            throw new BattleException(BattleErrorCode.INVALID_STATUS);
        }

        if (request.getBetAmount() == null) request.setBetAmount(BigDecimal.ZERO);
        request.setLevelMode(normalizeLevelMode(request.getLevelMode()));

        battleValidator.validateBetAmount(request.getBetAmount());

        String userLock = redisLockManager.lock(BattleRedisKeyUtil.userLockKey(userId));
        if (userLock == null) throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);

        try {
            String existingRoomId = stringRedisTemplate.opsForValue().get(BattleRedisKeyUtil.activeRoomKey(userId));
            if (existingRoomId != null) {
                Optional<BattleRoomState> existing = getRoomState(existingRoomId);
                if (existing.isPresent() && isActive(existing.get()) && isParticipant(existing.get(), userId)) {
                    log.info("[battle] matchId={} userId={} action=create idempotent", existing.get().getMatchId(), userId);
                    ensureNicknames(existing.get());
                    ensureActiveAndMembers(existing.get());
                    saveRoom(existing.get());
                    return BattleRoomResponse.from(existing.get());
                }
                cleanupActiveMapping(userId, existingRoomId, existing.orElse(null));
            }

            LocalDateTime now = BattleTime.nowKst();
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
                    .grade(fetchUserGrade(userId))
                    .ready(false)
                    .finished(false)
                    .build();

            Long requestedProblemId = normalizeProblemId(request.getAlgoProblemId());
            boolean randomProblem = requestedProblemId == null;
            Long resolvedProblemId = randomProblem ? selectRandomProblemId() : requestedProblemId;
            Integer maxMinutes = battleDurationPolicy.effectiveMinutes(
                    request.getMaxDurationMinutes(),
                    findProblemDifficulty(resolvedProblemId)
            );

            BattleRoomState state = BattleRoomState.builder()
                    .roomId(matchId)
                    .matchId(matchId)
                    .title(request.getTitle())
                    .status(BattleStatus.WAITING)
                    .hostUserId(userId)
                    .algoProblemId(resolvedProblemId)
                    .randomProblem(randomProblem)
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
                setMatchRoomMapping(state.getMatchId(), state.getRoomId());
                saveRoom(state);

                addRoomToLobby(state.getRoomId());
                addMember(state.getRoomId(), userId);
                setActiveRoom(userId, state.getRoomId());

                ensureNicknames(state);
                ensureActiveAndMembers(state);
                saveRoom(state);

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
        if (roomIds == null || roomIds.isEmpty()) return List.of();

        List<BattleRoomResponse> rooms = new ArrayList<>();
        for (String roomId : roomIds) {
            Optional<BattleRoomState> stateOpt = getRoomState(roomId);
            if (stateOpt.isEmpty()) {
                removeRoomFromLobby(roomId);
                continue;
            }

            BattleRoomState state = stateOpt.get();
            ensureNicknames(state);
            ensureActiveAndMembers(state);

            if (state.getStatus() == BattleStatus.WAITING || state.getStatus() == BattleStatus.COUNTDOWN) {
                try {
                    rooms.add(BattleRoomResponse.from(state));
                } catch (Exception e) {
                    log.warn("[battle] roomId={} action=listRooms response-error={}", roomId, e.getMessage());
                }
            } else {
                removeRoomFromLobby(roomId);
            }

            saveRoom(state);
        }
        return rooms;
    }

    public BattleRoomResponse getRoom(String roomId, Long requesterId) {
        BattleRoomState state = getRoomState(roomId)
                .orElseThrow(() -> new BattleException(BattleErrorCode.ROOM_NOT_FOUND));

        ensureNicknames(state);

        // 조회 시에도 매핑/멤버가 꼬였으면 재보장
        if (requesterId != null && isParticipant(state, requesterId)) {
            setActiveRoom(requesterId, roomId);
            addMember(roomId, requesterId);
        }
        ensureActiveAndMembers(state);
        saveRoom(state);

        return BattleRoomResponse.from(state);
    }

    public Optional<BattleRoomResponse> findMyActiveRoom(Long userId) {
        if (userId == null) return Optional.empty();

        String roomId = stringRedisTemplate.opsForValue().get(BattleRedisKeyUtil.activeRoomKey(userId));
        if (roomId == null) return Optional.empty();

        Optional<BattleRoomState> stateOpt = getRoomState(roomId);
        if (stateOpt.isEmpty()) {
            cleanupActiveMapping(userId, roomId, null);
            return Optional.empty();
        }

        BattleRoomState state = stateOpt.get();
        LocalDateTime now = BattleTime.nowKst();

        if (state.getStatus() == BattleStatus.FINISHED || state.getStatus() == BattleStatus.CANCELED) {
            Boolean isMember = stringRedisTemplate.opsForSet().isMember(
                    BattleRedisKeyUtil.membersKey(roomId),
                    String.valueOf(userId)
            );
            if (!Boolean.TRUE.equals(isMember)) {
                cleanupActiveMapping(userId, roomId, state);
                return Optional.empty();
            }
        }

        boolean keep =
                isActive(state) ||
                        (state.getStatus() == BattleStatus.FINISHED
                                && state.getPostGameUntil() != null
                                && state.getPostGameUntil().isAfter(now));

        if (!keep) {
            cleanupActiveMapping(userId, roomId, state);
            return Optional.empty();
        }

        ensureNicknames(state);
        ensureActiveAndMembers(state);
        saveRoom(state);

        return Optional.of(BattleRoomResponse.from(state));
    }

    public BattleRoomResponse joinRoom(String roomId, Long userId, String password) {
        if (battlePenaltyService.hasPenalty(userId)) {
            throw new BattleException(BattleErrorCode.INVALID_STATUS);
        }

        String userLock = redisLockManager.lock(BattleRedisKeyUtil.userLockKey(userId));
        if (userLock == null) throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);

        try {
            String activeRoomId = stringRedisTemplate.opsForValue().get(BattleRedisKeyUtil.activeRoomKey(userId));
            if (activeRoomId != null && !Objects.equals(activeRoomId, roomId)) {
                Optional<BattleRoomState> existing = getRoomState(activeRoomId);
                if (existing.isPresent() && isActive(existing.get()) && isParticipant(existing.get(), userId)) {
                    ensureNicknames(existing.get());
                    ensureActiveAndMembers(existing.get());
                    saveRoom(existing.get());
                    log.info("[battle] matchId={} userId={} action=join idempotent-existing", existing.get().getMatchId(), userId);
                    return BattleRoomResponse.from(existing.get());
                }
                cleanupActiveMapping(userId, activeRoomId, existing.orElse(null));
            } else if (activeRoomId != null && Objects.equals(activeRoomId, roomId)) {
                Optional<BattleRoomState> same = getRoomState(roomId);
                if (same.isPresent() && isActive(same.get()) && isParticipant(same.get(), userId)) {
                    ensureNicknames(same.get());
                    ensureActiveAndMembers(same.get());
                    saveRoom(same.get());
                    log.info("[battle] matchId={} userId={} action=join idempotent-same", same.get().getMatchId(), userId);
                    return BattleRoomResponse.from(same.get());
                }
                cleanupActiveMapping(userId, roomId, same.orElse(null));
            }

            String roomLock = redisLockManager.lock(BattleRedisKeyUtil.lockKey(roomId));
            if (roomLock == null) throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);

            try {
                BattleRoomState state = getRoomState(roomId)
                        .orElseThrow(() -> new BattleException(BattleErrorCode.ROOM_NOT_FOUND));

                LocalDateTime now = BattleTime.nowKst();
                if (state.getStatus() == BattleStatus.FINISHED
                        && state.getPostGameUntil() != null
                        && state.getPostGameUntil().isAfter(now)
                        && !isParticipant(state, userId)) {
                    throw new BattleException(BattleErrorCode.POSTGAME_LOCK);
                }

                if (Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(
                        BattleRedisKeyUtil.kickedKey(roomId), String.valueOf(userId)))) {
                    throw new BattleException(BattleErrorCode.KICKED_REJOIN_BLOCKED);
                }

                if (state.isPrivate()) {
                    enforcePasswordRateLimit(roomId, userId);

                    if (password == null || !password.matches("^\\d{4}$")
                            || !passwordEncoder.matches(password, state.getPasswordHash())) {
                        registerPasswordFailure(roomId, userId);
                        randomDelay();
                        throw new BattleException(BattleErrorCode.INVALID_PASSWORD);
                    }
                    clearPasswordAttempts(roomId, userId);
                }

                boolean alreadyParticipant = isParticipant(state, userId);
                if (state.getStatus() == BattleStatus.RUNNING && alreadyParticipant) {
                    cancelDisconnectGrace(state.getRoomId(), userId);
                    saveRoom(state);
                    setActiveRoom(userId, roomId);
                    addMember(roomId, userId);
                    ensureNicknames(state);
                    ensureActiveAndMembers(state);
                    saveRoom(state);
                    battleMessageService.publishRoomState(state);
                    return BattleRoomResponse.from(state);
                }

                if (alreadyParticipant) {
                    ensureNicknames(state);
                    ensureActiveAndMembers(state);
                    saveRoom(state);
                    return BattleRoomResponse.from(state);
                }

                if (state.getStatus() != BattleStatus.WAITING) {
                    throw new BattleException(BattleErrorCode.JOIN_NOT_ALLOWED);
                }

                if (state.getGuestUserId() != null) {
                    throw new BattleException(BattleErrorCode.ROOM_FULL);
                }

                if (isSameGradeMode(state) && isGradeMismatch(state.getHostUserId(), userId)) {
                    throw new BattleException(BattleErrorCode.LEVEL_MISMATCH);
                }

                state.setGuestUserId(userId);
                state.addOrUpdateParticipant(BattleParticipantState.builder()
                        .userId(userId)
                        .nickname(fetchNickname(userId))
                        .grade(fetchUserGrade(userId))
                        .ready(false)
                        .finished(false)
                        .build());

                saveRoom(state);

                addMember(roomId, userId);
                setActiveRoom(userId, roomId);
                battleMatchService.updateParticipants(state.getMatchId(), state.getHostUserId(), state.getGuestUserId());

                ensureNicknames(state);
                ensureActiveAndMembers(state);
                saveRoom(state);

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
        if (userLock == null) throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);

        try {
            String lockToken = redisLockManager.lock(BattleRedisKeyUtil.lockKey(roomId));
            if (lockToken == null) throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);

            try {
                BattleRoomState state = getRoomState(roomId)
                        .orElseThrow(() -> new BattleException(BattleErrorCode.ROOM_NOT_FOUND));

                if (!isParticipant(state, userId)) throw new BattleException(BattleErrorCode.NOT_PARTICIPANT);
                if (state.getStatus() == BattleStatus.COUNTDOWN) throw new BattleException(BattleErrorCode.INVALID_STATUS);

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

                            battleMatchService.updateParticipants(state.getMatchId(), state.getHostUserId(), state.getGuestUserId());
                            saveRoom(state);

                            clearActiveRoom(userId, roomId);
                            removeMember(roomId, userId);

                            setActiveRoom(guestId, roomId);
                            addMember(roomId, guestId);

                            ensureNicknames(state);
                            ensureActiveAndMembers(state);
                            saveRoom(state);

                            addRoomToLobby(roomId);
                            broadcastLobby();
                            battleMessageService.publishRoomState(state);

                            stringRedisTemplate.opsForSet().remove(BattleRedisKeyUtil.kickedKey(roomId), String.valueOf(guestId));
                        } else {
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

                        battleMatchService.updateParticipants(state.getMatchId(), state.getHostUserId(), null);
                        saveRoom(state);

                        clearActiveRoom(userId, roomId);
                        removeMember(roomId, userId);

                        ensureNicknames(state);
                        ensureActiveAndMembers(state);
                        saveRoom(state);

                        addRoomToLobby(roomId);
                        broadcastLobby();
                        battleMessageService.publishRoomState(state);
                    }
                } else if (state.getStatus() == BattleStatus.RUNNING) {
                    startDisconnectGrace(state, userId);
                } else if (state.getStatus() == BattleStatus.FINISHED || state.getStatus() == BattleStatus.CANCELED) {
                    cancelDisconnectGrace(roomId, userId);
                    clearActiveRoom(userId, roomId);
                    removeMember(roomId, userId);
                    Optional.ofNullable(state.getParticipants()).ifPresent(map -> map.remove(userId));
                    if (Objects.equals(userId, state.getHostUserId())) {
                        state.setHostUserId(null);
                    }
                    if (Objects.equals(userId, state.getGuestUserId())) {
                        state.setGuestUserId(null);
                    }

                    Set<String> members = stringRedisTemplate.opsForSet()
                            .members(BattleRedisKeyUtil.membersKey(roomId));
                    boolean hasMembers = members != null && !members.isEmpty();
                    if (!hasMembers) {
                        cancelPostGameTask(roomId);
                        cleanupRoomKeys(roomId, participantIds(state));
                        broadcastLobby();
                    } else {
                        syncParticipantsWithMembers(state, members);
                        ensureNicknames(state);
                        ensureActiveAndMembers(state);
                        saveRoom(state);
                        battleMessageService.publishRoomState(state);
                        broadcastLobby();
                    }
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
        if (userLock == null) throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);

        try {
            String lockToken = redisLockManager.lock(BattleRedisKeyUtil.lockKey(roomId));
            if (lockToken == null) throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);

            try {
                BattleRoomState state = getRoomState(roomId)
                        .orElseThrow(() -> new BattleException(BattleErrorCode.ROOM_NOT_FOUND));

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
                    battleMatchService.updateParticipants(state.getMatchId(), state.getHostUserId(), null);
                }

                state.setCountdownStarted(false);
                state.setStatus(BattleStatus.WAITING);

                ensureNicknames(state);
                ensureActiveAndMembers(state);
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

    public BattleRoomResponse resetRoomForParticipant(String roomId, Long userId) {
        String lockToken = redisLockManager.lock(BattleRedisKeyUtil.lockKey(roomId));
        if (lockToken == null) throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);

        try {
            BattleRoomState state = getRoomState(roomId)
                    .orElseThrow(() -> new BattleException(BattleErrorCode.ROOM_NOT_FOUND));

            if (!isParticipant(state, userId)) throw new BattleException(BattleErrorCode.NOT_PARTICIPANT);

            if (state.getStatus() == BattleStatus.FINISHED || state.getStatus() == BattleStatus.CANCELED) {
                resetForNextMatch(state);
            }

            cancelDisconnectGrace(roomId, userId);
            setActiveRoom(userId, roomId);
            addMember(roomId, userId);

            ensureNicknames(state);
            ensureActiveAndMembers(state);
            saveRoom(state);

            battleMessageService.publishRoomState(state);
            return BattleRoomResponse.from(state);

        } finally {
            redisLockManager.unlock(BattleRedisKeyUtil.lockKey(roomId), lockToken);
        }
    }

    public BattleRoomResponse ready(String roomId, Long userId, boolean ready) {
        if (battlePenaltyService.hasPenalty(userId)) {
            throw new BattleException(BattleErrorCode.INVALID_STATUS);
        }

        String userLock = redisLockManager.lock(BattleRedisKeyUtil.userLockKey(userId));
        if (userLock == null) throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);

        try {
            String lockToken = redisLockManager.lock(BattleRedisKeyUtil.lockKey(roomId));
            if (lockToken == null) throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);

            try {
                BattleRoomState state = getRoomState(roomId)
                        .orElseThrow(() -> new BattleException(BattleErrorCode.ROOM_NOT_FOUND));

                if (state.getStatus() != BattleStatus.WAITING && !(state.getStatus() == BattleStatus.COUNTDOWN && !ready)) {
                    throw new BattleException(BattleErrorCode.READY_NOT_ALLOWED);
                }
                if (!isParticipant(state, userId)) {
                    throw new BattleException(BattleErrorCode.NOT_PARTICIPANT);
                }
                if (state.getReadyCooldownUntil() != null
                        && state.getReadyCooldownUntil().isAfter(BattleTime.nowKst())) {
                    throw new BattleException(BattleErrorCode.READY_COOLDOWN);
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

                setActiveRoom(userId, roomId);
                addMember(roomId, userId);

                ensureNicknames(state);
                ensureActiveAndMembers(state);
                saveRoom(state);

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

        if (message.getSource() == null || message.getSource().isBlank()) {
            throw new BattleException(BattleErrorCode.SUBMIT_PARAM_INVALID);
        }

        if (battlePenaltyService.hasPenalty(userId)) {
            throw new BattleException(BattleErrorCode.INVALID_STATUS);
        }

        String userLock = redisLockManager.lock(BattleRedisKeyUtil.userLockKey(userId));
        if (userLock == null) throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);

        BattleRoomState state;
        LocalDateTime submittedAt = null;
        Long elapsedSeconds = null;

        try {
            String lockToken = redisLockManager.lock(BattleRedisKeyUtil.lockKey(roomId));
            if (lockToken == null) throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);

            try {
                state = getRoomState(roomId)
                        .orElseThrow(() -> new BattleException(BattleErrorCode.ROOM_NOT_FOUND));

                if (state.getStatus() != BattleStatus.RUNNING) throw new BattleException(BattleErrorCode.NOT_RUNNING);
                if (!isParticipant(state, userId)) throw new BattleException(BattleErrorCode.NOT_PARTICIPANT);

                BattleParticipantState participant = Optional.ofNullable(state.participant(userId))
                        .orElseGet(() -> BattleParticipantState.builder().userId(userId).build());
                if (participant.isFinished()) {
                    throw new BattleException(BattleErrorCode.SUBMIT_ALREADY_FINISHED);
                }

                LocalDateTime now = BattleTime.nowKst();
                if (participant.getLastSubmittedAt() != null
                        && Duration.between(participant.getLastSubmittedAt(), now).compareTo(SUBMIT_COOLDOWN) < 0) {
                    throw new BattleException(BattleErrorCode.SUBMIT_COOLDOWN);
                }

                participant.setLastSubmittedAt(now);
                submittedAt = now;

                if (state.getStartedAt() != null) {
                    elapsedSeconds = Duration.between(state.getStartedAt(), now).getSeconds();
                    participant.setElapsedSeconds(elapsedSeconds);
                }

                // 여기서 "채점 결과"에 따라 finished 처리/승부 처리
                // - accepted면 finish 트리거
                // - rejected면 finished=false 유지 (계속 진행)
                participant.setFinished(false);
                state.addOrUpdateParticipant(participant);

                setActiveRoom(userId, roomId);
                addMember(roomId, userId);

                ensureNicknames(state);
                ensureActiveAndMembers(state);
                saveRoom(state);

            } finally {
                redisLockManager.unlock(BattleRedisKeyUtil.lockKey(roomId), lockToken);
            }

        } finally {
            redisLockManager.unlock(BattleRedisKeyUtil.userLockKey(userId), userLock);
        }

        // 채점은 락 밖에서
        BattleJudgeResult result;
        try {
            result = battleJudgeService.judge(state.getMatchId(), userId, message);
        } catch (Exception e) {
            result = BattleJudgeResult.rejected(JUDGE_RETRY_MESSAGE);
        }

        if (result == null || !result.isAccepted()) {
            result = BattleJudgeResult.rejected(JUDGE_RETRY_MESSAGE);
        }

        BigDecimal baseScore = deriveBaseScore(result);
        boolean accepted = result != null && result.isAccepted();
        String judgeDetail = normalizeJudgeDetail(result != null ? result.getMessage() : null);
        String judgeSummary = accepted ? buildJudgeSummary(baseScore, judgeDetail) : JUDGE_RETRY_MESSAGE;
        if (!accepted) {
            baseScore = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            judgeDetail = JUDGE_RETRY_MESSAGE;
        }

        // 결과 반영
        applyJudgeResultAndMaybeFinish(state.getRoomId(), userId, submittedAt != null ? submittedAt : BattleTime.nowKst(),
                elapsedSeconds, baseScore, result);

        BattleSubmitResultResponse submitResult = BattleSubmitResultResponse.builder()
                .userId(userId)
                .submittedAt(BattleTime.toIsoKst(submittedAt != null ? submittedAt : BattleTime.nowKst()))
                .elapsedSeconds(elapsedSeconds)
                .baseScore(baseScore)
                .timeBonus(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .finalScore(baseScore)
                .message(judgeSummary)
                .judgeSummary(judgeSummary)
                .judgeDetail(judgeDetail)
                .accepted(accepted)
                .build();

        battleMessageService.publishSubmitResult(submitResult, state.getRoomId(), state.getMatchId());

        // accepted가 아니면 룸 상태만 리턴
        return BattleRoomResponse.from(getRoomState(roomId).orElse(state));
    }

    public BattleRoomResponse surrender(String roomId, Long userId) {
        String lockToken = redisLockManager.lock(BattleRedisKeyUtil.lockKey(roomId));
        if (lockToken == null) throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);

        Long winnerUserId;
        try {
            BattleRoomState state = getRoomState(roomId)
                    .orElseThrow(() -> new BattleException(BattleErrorCode.ROOM_NOT_FOUND));

            if (state.getStatus() != BattleStatus.RUNNING && state.getStatus() != BattleStatus.COUNTDOWN) {
                throw new BattleException(BattleErrorCode.INVALID_STATUS);
            }
            if (!isParticipant(state, userId)) throw new BattleException(BattleErrorCode.NOT_PARTICIPANT);

            winnerUserId = Objects.equals(userId, state.getHostUserId())
                    ? state.getGuestUserId()
                    : state.getHostUserId();
            if (winnerUserId == null) throw new BattleException(BattleErrorCode.INVALID_STATUS);

            BattleParticipantState participant = state.participant(userId);
            if (participant != null) {
                participant.setSurrendered(true);
                participant.setFinished(true);
                state.addOrUpdateParticipant(participant);
            }

            ensureNicknames(state);
            ensureActiveAndMembers(state);
            saveRoom(state);

        } finally {
            redisLockManager.unlock(BattleRedisKeyUtil.lockKey(roomId), lockToken);
        }

        // finish after releasing the room lock to avoid lock reentry failures
        return finishWithReason(roomId, winnerUserId, "SURRENDER");
    }

    public BattleRoomResponse updateSettings(String roomId, Long userId, BattleRoomUpdateRequest request) {
        String lockToken = redisLockManager.lock(BattleRedisKeyUtil.lockKey(roomId));
        if (lockToken == null) throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);

        try {
            BattleRoomState state = getRoomState(roomId)
                    .orElseThrow(() -> new BattleException(BattleErrorCode.ROOM_NOT_FOUND));

            if (!Objects.equals(state.getHostUserId(), userId)) throw new BattleException(BattleErrorCode.NOT_PARTICIPANT);
            if (state.getStatus() == BattleStatus.COUNTDOWN) {
                throw new BattleException(BattleErrorCode.SETTINGS_LOCKED);
            }
            if (state.getStatus() != BattleStatus.WAITING) {
                throw new BattleException(BattleErrorCode.INVALID_STATUS);
            }

            Long previousProblemId = state.getAlgoProblemId();
            Long resolvedProblemId = applyProblemSelection(state, request.getAlgoProblemId());

            if (request.getTitle() != null) state.setTitle(request.getTitle());
            if (request.getLanguageId() != null) state.setLanguageId(request.getLanguageId());
            if (request.getLevelMode() != null && !request.getLevelMode().isBlank()) {
                state.setLevelMode(normalizeLevelMode(request.getLevelMode()));
            }

            if (!Objects.equals(previousProblemId, resolvedProblemId)) {
                battleMatchService.updateProblem(state.getMatchId(), resolvedProblemId);
            }

            if (request.getBetAmount() != null) {
                battleValidator.validateBetAmount(request.getBetAmount());
                state.setBetAmount(request.getBetAmount());
            }

            if (request.getMaxDurationMinutes() != null) {
                int minutes = battleDurationPolicy.effectiveMinutes(request.getMaxDurationMinutes(), findProblemDifficulty(state.getAlgoProblemId()));
                state.setMaxDurationMinutes(minutes);
                battleMatchService.updateMaxDuration(state.getMatchId(), minutes);
                rescheduleTimeoutIfRunning(state); // reschedule if running
            }

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

            state.setCountdownStarted(false);
            if (state.getParticipants() != null) {
                state.getParticipants().values().forEach(participant -> {
                    if (participant != null) {
                        participant.setReady(false);
                    }
                });
            }
            state.setReadyCooldownUntil(BattleTime.nowKst().plusSeconds(3));

            ensureNicknames(state);
            ensureActiveAndMembers(state);
            saveRoom(state);

            broadcastLobby();
            battleMessageService.publishRoomState(state);

            if (isSameGradeMode(state) && state.getGuestUserId() != null
                    && isGradeMismatch(state.getHostUserId(), state.getGuestUserId())) {
                Long guestId = state.getGuestUserId();
                state.setGuestUserId(null);
                Optional.ofNullable(state.getParticipants()).ifPresent(map -> map.remove(guestId));
                clearActiveRoom(guestId, roomId);
                removeMember(roomId, guestId);
                battleMatchService.updateParticipants(state.getMatchId(), state.getHostUserId(), null);

                ensureNicknames(state);
                ensureActiveAndMembers(state);
                saveRoom(state);

                battleMessageService.publishRoomState(state);
                broadcastLobby();
                battleMessageService.sendErrorToUser(
                        guestId,
                        BattleErrorCode.LEVEL_MISMATCH,
                        "\uBC29 \uC124\uC815 \uBCC0\uACBD \uADDC\uCE59\uACFC \uB2EC\uB77C \uBC29\uC5D0\uC11C \uB098\uAC00\uC9D1\uB2C8\uB2E4."
                );
            }

            return BattleRoomResponse.from(state);

        } finally {
            redisLockManager.unlock(BattleRedisKeyUtil.lockKey(roomId), lockToken);
        }
    }

    /* ------------------------------------------------------------
     * Countdown / Match start / Timeout
     * ------------------------------------------------------------ */

    private boolean bothReady(BattleRoomState state) {
        if (state.getHostUserId() == null || state.getGuestUserId() == null) return false;
        BattleParticipantState host = state.participant(state.getHostUserId());
        BattleParticipantState guest = state.participant(state.getGuestUserId());
        return host != null && host.isReady() && guest != null && guest.isReady();
    }

    private void startCountdown(BattleRoomState state) {
        BigDecimal bet = state.getBetAmount();
        if (bet == null || bet.compareTo(BigDecimal.ZERO) < 0) bet = BigDecimal.ZERO;

        if (bet.compareTo(BigDecimal.ZERO) > 0) {
            try {
                holdUserBet(state, state.getHostUserId(), bet);
                if (state.getGuestUserId() != null) {
                    holdUserBet(state, state.getGuestUserId(), bet);
                }
                battleMatchService.updateSettlementStatus(state.getMatchId(), BattleSettlementStatus.HELD);
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

        ensureNicknames(state);
        ensureActiveAndMembers(state);
        saveRoom(state);

        battleMatchService.markCountdown(state.getMatchId());
        battleMessageService.publishRoomState(state);
        broadcastLobby();

        // 카운트다운 메시지
        for (int i = COUNTDOWN_SECONDS; i >= 1; i--) {
            int secondsLeft = i;
            battleTaskScheduler.schedule(
                    () -> getRoomState(state.getRoomId())
                            .filter(current -> current.getStatus() == BattleStatus.COUNTDOWN)
                            .ifPresent(current -> battleMessageService.publishCountdown(current, secondsLeft)),
                    Instant.now().plusSeconds(COUNTDOWN_SECONDS - i)
            );
        }

        // 매치 시작 예약
        battleTaskScheduler.schedule(
                () -> startMatch(state.getRoomId()),
                Instant.now().plusSeconds(COUNTDOWN_SECONDS)
        );
    }

    private void startMatch(String roomId) {
        String lockToken = redisLockManager.lock(BattleRedisKeyUtil.lockKey(roomId));
        if (lockToken == null) return;

        try {
            BattleRoomState state = getRoomState(roomId).orElse(null);
            if (state == null || state.getStatus() != BattleStatus.COUNTDOWN) return;

            if (!bothReady(state)) {
                state.setCountdownStarted(false);
                state.setStatus(BattleStatus.WAITING);

                battleSettlementService.refundAll(state);

                ensureNicknames(state);
                ensureActiveAndMembers(state);
                saveRoom(state);

                battleMessageService.publishRoomState(state);
                broadcastLobby();
                return;
            }

            state.setStatus(BattleStatus.RUNNING);
            state.setStartedAt(BattleTime.nowKst());

            ensureNicknames(state);
            ensureActiveAndMembers(state);
            saveRoom(state);

            battleMatchService.markRunning(state.getMatchId());
            removeRoomFromLobby(state.getRoomId());

            scheduleTimeout(state); // RUNNING 기준으로 deadline 스케줄
            battleMessageService.publishStart(state);
            battleMessageService.publishRoomState(state);
            broadcastLobby();

            log.info("[battle] matchId={} action=start state={}", state.getMatchId(), state.getStatus());

        } finally {
            redisLockManager.unlock(BattleRedisKeyUtil.lockKey(roomId), lockToken);
        }
    }

    private void scheduleTimeout(BattleRoomState state) {
        cancelTimeoutTask(state.getRoomId());

        Integer maxDuration = state.getMaxDurationMinutes();
        if (maxDuration == null || maxDuration <= 0) {
            maxDuration = battleDurationPolicy.defaultMinutes(null);
        }

        // 핵심: 저장은 LocalDateTime, 예약만 Instant
        LocalDateTime startedAt = state.getStartedAt();
        if (startedAt == null) {
            startedAt = BattleTime.nowKst();
            state.setStartedAt(startedAt);
            saveRoom(state);
        }

        LocalDateTime deadlineLdt = startedAt.plusMinutes(maxDuration);
        Instant triggerAt = BattleTime.toInstant(deadlineLdt);

        ScheduledFuture<?> future = battleTaskScheduler.schedule(
                () -> finishByTimeout(state.getRoomId()),
                triggerAt
        );
        timeoutTasks.put(state.getRoomId(), future);
    }

    private void rescheduleTimeoutIfRunning(BattleRoomState state) {
        if (state == null) return;
        if (state.getStatus() != BattleStatus.RUNNING) return;
        scheduleTimeout(state);
    }

    private void cancelTimeoutTask(String roomId) {
        Optional.ofNullable(timeoutTasks.remove(roomId)).ifPresent(f -> f.cancel(false));
    }

    private void finishByTimeout(String roomId) {
        String lockToken = redisLockManager.lock(BattleRedisKeyUtil.lockKey(roomId));
        if (lockToken == null) return;

        try {
            BattleRoomState state = getRoomState(roomId).orElse(null);
            if (state == null || state.getStatus() != BattleStatus.RUNNING) return;

            // TIMEOUT은 무승부(환불)
            state.setStatus(BattleStatus.FINISHED);
            state.setWinReason("TIMEOUT");
            state.setWinnerUserId(null);
            state.setFinishedAt(BattleTime.nowKst());
            state.setPostGameUntil(BattleTime.nowKst().plusSeconds(POSTGAME_LOCK_DURATION.getSeconds()));

            // 타임아웃 시 점수 0으로 설정
            setTimeoutScores(state);

            ensureNicknames(state);
            ensureActiveAndMembers(state);
            saveRoom(state);

            cancelTimeoutTask(roomId);
            removeRoomFromLobby(roomId);

            battleSettlementService.refundAll(state);
            battleMatchService.finishMatch(state.getMatchId(), null, "TIMEOUT");
            battleMessageService.publishFinish(state);
            broadcastLobby();

            // 포스트게임 끝나고 정리
            schedulePostGameCleanup(state);

            log.info("[battle] matchId={} action=timeout-finish", state.getMatchId());

        } finally {
            redisLockManager.unlock(BattleRedisKeyUtil.lockKey(roomId), lockToken);
        }
    }

    // 새로 추가할 메서드 (BattleRoomService 클래스 안 어디든 추가)
    private void setTimeoutScores(BattleRoomState state) {
        // Host
        if (state.getHostUserId() != null) {
            BattleParticipantState host = state.participant(state.getHostUserId());
            if (host != null) {
                host.setBaseScore(BigDecimal.ZERO);
                host.setTimeBonus(BigDecimal.ZERO);
                host.setFinalScore(BigDecimal.ZERO);
                state.addOrUpdateParticipant(host);
            }
        }

        // Guest
        if (state.getGuestUserId() != null) {
            BattleParticipantState guest = state.participant(state.getGuestUserId());
            if (guest != null) {
                guest.setBaseScore(BigDecimal.ZERO);
                guest.setTimeBonus(BigDecimal.ZERO);
                guest.setFinalScore(BigDecimal.ZERO);
                state.addOrUpdateParticipant(guest);
            }
        }
    }

    /* ------------------------------------------------------------
     * Finish logic (Accepted / Disconnect / Surrender)
     * ------------------------------------------------------------ */

    public BattleRoomResponse finishWithWinner(String roomId, Long winnerUserId) {
        return finishWithReason(roomId, winnerUserId, "ACCEPTED");
    }

    private BattleRoomResponse finishWithReason(String roomId, Long winnerUserId, String reason) {
        String lockToken = redisLockManager.lock(BattleRedisKeyUtil.lockKey(roomId));
        if (lockToken == null) throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);

        try {
            BattleRoomState state = getRoomState(roomId)
                    .orElseThrow(() -> new BattleException(BattleErrorCode.ROOM_NOT_FOUND));

            // 이미 FINISHED면 그대로 리턴(포스트게임 유지)
            if (state.getStatus() == BattleStatus.FINISHED) {
                ensureNicknames(state);
                ensureActiveAndMembers(state);
                saveRoom(state);
                return BattleRoomResponse.from(state);
            }

            if (!isParticipant(state, winnerUserId)) throw new BattleException(BattleErrorCode.NOT_PARTICIPANT);

            Long loserUserId = Objects.equals(winnerUserId, state.getHostUserId())
                    ? state.getGuestUserId()
                    : state.getHostUserId();

            BattleParticipantState winnerState = state.participant(winnerUserId);
            if (winnerState != null) {
                winnerState.setFinished(true);
                state.addOrUpdateParticipant(winnerState);
            }

            BattleParticipantState loserState = loserUserId != null ? state.participant(loserUserId) : null;
            if (loserState != null) {
                loserState.setFinished(true);
                if ("SURRENDER".equalsIgnoreCase(reason) || "DISCONNECT".equalsIgnoreCase(reason)) {
                    loserState.setSurrendered(true);
                }
                state.addOrUpdateParticipant(loserState);
            }

            state.setWinnerUserId(winnerUserId);
            state.setWinReason(reason);
            state.setFinishedAt(BattleTime.nowKst());
            state.setPostGameUntil(BattleTime.nowKst().plusSeconds(POSTGAME_LOCK_DURATION.getSeconds()));
            state.setStatus(BattleStatus.FINISHED);

            // 점수 정보 설정 (승자/패자 모두)
            setFinishScores(state, winnerUserId, loserUserId);

            ensureNicknames(state);
            ensureActiveAndMembers(state);
            saveRoom(state);

            removeRoomFromLobby(state.getRoomId());
            cancelTimeoutTask(state.getRoomId());

            // 정산/DB/알림
            Integer winnerElapsedMs = calculateWinnerElapsedMs(state, winnerUserId);
            if (winnerElapsedMs != null) {
                battleMatchService.updateWinnerElapsedMs(state.getMatchId(), winnerElapsedMs);
            }
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

            // "즉시 삭제" 금지 -> 포스트게임 후 정리
            schedulePostGameCleanup(state);

            log.info("[battle] matchId={} userId={} action=finish reason={} state={}",
                    state.getMatchId(), winnerUserId, reason, state.getStatus());

            return BattleRoomResponse.from(state);

        } finally {
            redisLockManager.unlock(BattleRedisKeyUtil.lockKey(roomId), lockToken);
        }
    }

    private BattleRoomResponse finishAsDraw(String roomId) {
        String lockToken = redisLockManager.lock(BattleRedisKeyUtil.lockKey(roomId));
        if (lockToken == null) throw new BattleException(BattleErrorCode.LOCK_TIMEOUT);

        try {
            BattleRoomState state = getRoomState(roomId)
                    .orElseThrow(() -> new BattleException(BattleErrorCode.ROOM_NOT_FOUND));

            if (state.getStatus() == BattleStatus.FINISHED) {
                ensureNicknames(state);
                ensureActiveAndMembers(state);
                saveRoom(state);
                return BattleRoomResponse.from(state);
            }

            state.setWinnerUserId(null);
            state.setWinReason("DRAW");
            state.setFinishedAt(BattleTime.nowKst());
            state.setPostGameUntil(BattleTime.nowKst().plusSeconds(POSTGAME_LOCK_DURATION.getSeconds()));
            state.setStatus(BattleStatus.FINISHED);

            ensureNicknames(state);
            ensureActiveAndMembers(state);
            saveRoom(state);

            removeRoomFromLobby(state.getRoomId());
            cancelTimeoutTask(state.getRoomId());

            battleSettlementService.refundAll(state);
            battleMatchService.finishMatch(state.getMatchId(), null, "DRAW");

            battleMessageService.publishFinish(state);
            broadcastLobby();

            battlePenaltyService.recordNormalFinish(state.getHostUserId());
            battlePenaltyService.recordNormalFinish(state.getGuestUserId());

            schedulePostGameCleanup(state);

            log.info("[battle] matchId={} action=finish-draw", state.getMatchId());

            return BattleRoomResponse.from(state);

        } finally {
            redisLockManager.unlock(BattleRedisKeyUtil.lockKey(roomId), lockToken);
        }
    }

    // 새로 추가할 메서드 (BattleRoomService 클래스 안 어디든 추가)
    private void setFinishScores(BattleRoomState state, Long winnerUserId, Long loserUserId) {
        BigDecimal zeroScore = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        // 승자 점수 설정
        if (winnerUserId != null) {
            BattleParticipantState winner = state.participant(winnerUserId);
            if (winner != null) {
                if (winner.getBaseScore() == null) {
                    winner.setBaseScore(zeroScore);
                }
                if (winner.getTimeBonus() == null) {
                    winner.setTimeBonus(zeroScore);
                }
                if (winner.getFinalScore() == null) {
                    winner.setFinalScore(winner.getBaseScore().add(winner.getTimeBonus()));
                }
                state.addOrUpdateParticipant(winner);
            }
        }

        // 패자 점수 설정
        if (loserUserId != null) {
            BattleParticipantState loser = state.participant(loserUserId);
            if (loser != null) {
                if (loser.getBaseScore() == null) {
                    loser.setBaseScore(zeroScore);
                }
                if (loser.getTimeBonus() == null) {
                    loser.setTimeBonus(zeroScore);
                }
                if (loser.getFinalScore() == null) {
                    loser.setFinalScore(loser.getBaseScore().add(loser.getTimeBonus()));
                }
                state.addOrUpdateParticipant(loser);
            }
        }
    }

    private void schedulePostGameCleanup(BattleRoomState state) {
        if (state == null || state.getRoomId() == null) return;

        cancelPostGameTask(state.getRoomId());

        ScheduledFuture<?> future = battleTaskScheduler.schedule(
                () -> cleanupAfterPostGame(state.getRoomId()),
                Instant.now().plus(POSTGAME_LOCK_DURATION)
        );
        postGameTasks.put(state.getRoomId(), future);
    }

    private void cancelPostGameTask(String roomId) {
        Optional.ofNullable(postGameTasks.remove(roomId)).ifPresent(f -> f.cancel(false));
    }

    private void cleanupAfterPostGame(String roomId) {
        String lockToken = redisLockManager.lock(BattleRedisKeyUtil.lockKey(roomId));
        if (lockToken == null) return;

        try {
            BattleRoomState state = getRoomState(roomId).orElse(null);
            if (state == null) return;

            LocalDateTime now = BattleTime.nowKst();
            // 포스트게임 시간이 남아있으면 재예약
            if (state.getStatus() == BattleStatus.FINISHED
                    && state.getPostGameUntil() != null
                    && state.getPostGameUntil().isAfter(now)) {
                schedulePostGameCleanup(state);
                return;
            }

            Set<String> members = stringRedisTemplate.opsForSet().members(BattleRedisKeyUtil.membersKey(roomId));
            boolean hasMembers = members != null && !members.isEmpty();

            if ((state.getStatus() == BattleStatus.FINISHED || state.getStatus() == BattleStatus.CANCELED) && hasMembers) {
                syncParticipantsWithMembers(state, members);
                resetForNextMatch(state);
                ensureNicknames(state);
                ensureActiveAndMembers(state);
                saveRoom(state);
                battleMessageService.publishRoomState(state);
                broadcastLobby();
                log.info("[battle] roomId={} action=postgame-reset keepMembers={}", roomId, members.size());
                return;
            }

            cleanupRoomKeys(roomId, participantIds(state));
            log.info("[battle] roomId={} action=postgame-cleanup", roomId);

        } finally {
            redisLockManager.unlock(BattleRedisKeyUtil.lockKey(roomId), lockToken);
            cancelPostGameTask(roomId);
        }
    }

    /* ------------------------------------------------------------
     * Judge result apply
     * ------------------------------------------------------------ */

    private BigDecimal deriveBaseScore(BattleJudgeResult result) {
        if (result == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal score = result.getScore();
        if (score == null) {
            score = result.isAccepted() ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private void applyJudgeResultAndMaybeFinish(
            String roomId,
            Long userId,
            LocalDateTime submittedAt,
            Long elapsedSeconds,
            BigDecimal baseScore,
            BattleJudgeResult result
    ) {
        applyJudgeResultAndMaybeFinish(roomId, userId, submittedAt, elapsedSeconds, baseScore, result, 3);
    }

    private void applyJudgeResultAndMaybeFinish(
            String roomId,
            Long userId,
            LocalDateTime submittedAt,
            Long elapsedSeconds,
            BigDecimal baseScore,
            BattleJudgeResult result,
            int remainingRetries
    ) {
        String lockToken = redisLockManager.lock(BattleRedisKeyUtil.lockKey(roomId));
        if (lockToken == null) {
            if (remainingRetries > 0) {
                battleTaskScheduler.schedule(
                        () -> applyJudgeResultAndMaybeFinish(
                                roomId, userId, submittedAt, elapsedSeconds, baseScore, result, remainingRetries - 1),
                        Instant.now().plusMillis(200)
                );
            } else if (userId != null) {
                battleMessageService.sendErrorToUser(
                        userId,
                        BattleErrorCode.LOCK_TIMEOUT,
                        "\uCC44\uC810 \uCC98\uB9AC\uC5D0 \uC2E4\uD328\uD588\uC2B5\uB2C8\uB2E4. \uB2E4\uC2DC \uC81C\uCD9C\uD574 \uC8FC\uC138\uC694."
                );
            }
            return;
        }

        boolean judged = false;
        boolean shouldFinish = false;
        boolean draw = false;
        Long winnerUserId = null;
        try {
            BattleRoomState state = getRoomState(roomId).orElse(null);
            if (state == null) return;

            BattleParticipantState participant = state.participant(userId);
            if (participant == null) return;

            participant.setLastSubmittedAt(submittedAt);
            participant.setElapsedSeconds(elapsedSeconds);

            boolean accepted = result != null && result.isAccepted();
            String normalizedMessage = normalizeJudgeDetail(result != null ? result.getMessage() : null);
            participant.setBaseScore(baseScore);
            participant.setTimeBonus(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            participant.setFinalScore(baseScore);
            if (accepted) {
                participant.setJudgeMessage(buildJudgeSummary(baseScore, normalizedMessage));
                participant.setJudgeErrorCount(0);
                battleMatchService.recordAccepted(
                        state.getMatchId(),
                        userId,
                        state.getHostUserId(),
                        state.getGuestUserId(),
                        submittedAt
                );
            } else {
                participant.setJudgeMessage(normalizedMessage != null ? normalizedMessage : JUDGE_RETRY_MESSAGE);
                Integer errorCount = participant.getJudgeErrorCount();
                int nextCount = errorCount == null ? 1 : errorCount + 1;
                participant.setJudgeErrorCount(nextCount);
                if (nextCount >= JUDGE_ERROR_LIMIT) {
                    shouldFinish = true;
                    draw = true;
                }
            }

            judged = accepted;
            participant.setFinished(accepted);

            state.addOrUpdateParticipant(participant);

            if (!accepted && shouldFinish && state.getHostUserId() != null && state.getGuestUserId() != null) {
                BigDecimal zeroScore = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                BattleParticipantState host = state.participant(state.getHostUserId());
                BattleParticipantState guest = state.participant(state.getGuestUserId());
                if (host != null) {
                    host.setBaseScore(zeroScore);
                    host.setTimeBonus(zeroScore);
                    host.setFinalScore(zeroScore);
                    host.setFinished(true);
                    state.addOrUpdateParticipant(host);
                }
                if (guest != null) {
                    guest.setBaseScore(zeroScore);
                    guest.setTimeBonus(zeroScore);
                    guest.setFinalScore(zeroScore);
                    guest.setFinished(true);
                    state.addOrUpdateParticipant(guest);
                }
            }

            if (judged && state.getHostUserId() != null && state.getGuestUserId() != null) {
                BattleParticipantState host = state.participant(state.getHostUserId());
                BattleParticipantState guest = state.participant(state.getGuestUserId());
                if (host != null && guest != null && host.isFinished() && guest.isFinished()) {
                    applyTimeBonus(host, guest);
                    state.addOrUpdateParticipant(host);
                    state.addOrUpdateParticipant(guest);
                    int cmp = compareFinalScore(host, guest);
                    if (cmp > 0) {
                        winnerUserId = state.getHostUserId();
                    } else if (cmp < 0) {
                        winnerUserId = state.getGuestUserId();
                    } else {
                        int timeCmp = compareElapsed(host, guest);
                        if (timeCmp > 0) {
                            winnerUserId = state.getHostUserId();
                        } else if (timeCmp < 0) {
                            winnerUserId = state.getGuestUserId();
                        } else {
                            draw = true;
                        }
                    }
                    shouldFinish = true;
                }
            }

            ensureNicknames(state);
            ensureActiveAndMembers(state);
            saveRoom(state);

        } finally {
            redisLockManager.unlock(BattleRedisKeyUtil.lockKey(roomId), lockToken);
        }

        if (shouldFinish) {
            try {
                if (draw) {
                    finishAsDraw(roomId);
                } else if (winnerUserId != null) {
                    // finish after releasing the room lock to avoid lock reentry failures
                    finishWithReason(roomId, winnerUserId, "SCORE");
                }
            } catch (BattleException ex) {
                log.warn("[battle] matchId={} userId={} action=finish-after-judge errorCode={}",
                        roomId, userId, ex.getErrorCode().getCode());
            }
        }
    }

    private void resetForNextMatch(BattleRoomState state) {
        cancelTimeoutTask(state.getRoomId());
        cancelPostGameTask(state.getRoomId());

        state.setStatus(BattleStatus.WAITING);
        state.setCountdownStarted(false);
        state.setWinnerUserId(null);
        state.setWinReason(null);
        state.setStartedAt(null);
        state.setFinishedAt(null);
        state.setPostGameUntil(null);
        state.setReadyCooldownUntil(null);

        if (state.getParticipants() != null) {
            state.getParticipants().values().forEach(participant -> {
                if (participant == null) return;
                participant.setReady(false);
                participant.setSurrendered(false);
                participant.setFinished(false);
                participant.setLastSubmittedAt(null);
                participant.setElapsedSeconds(null);
                participant.setBaseScore(null);
                participant.setTimeBonus(null);
                participant.setFinalScore(null);
                participant.setJudgeMessage(null);
                participant.setJudgeErrorCount(null);
            });
        }

        if (state.isRandomProblem()) {
            state.setAlgoProblemId(selectRandomProblemId());
        }

        String previousMatchId = state.getMatchId();
        String newMatchId = UUID.randomUUID().toString();
        state.setMatchId(newMatchId);
        battleMatchService.createMatch(state);
        clearMatchRoomMapping(previousMatchId);
        setMatchRoomMapping(newMatchId, state.getRoomId());

        addRoomToLobby(state.getRoomId());
    }

    private void applyTimeBonus(BattleParticipantState host, BattleParticipantState guest) {
        if (host == null || guest == null) return;
        Long hostElapsed = host.getElapsedSeconds();
        Long guestElapsed = guest.getElapsedSeconds();
        if (hostElapsed == null || guestElapsed == null) return;

        long diff = guestElapsed - hostElapsed;
        BigDecimal hostBonus = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal guestBonus = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (diff > 0) {
            hostBonus = BigDecimal.valueOf(diff).multiply(TIME_BONUS_PER_SECOND).setScale(2, RoundingMode.HALF_UP);
        } else if (diff < 0) {
            guestBonus = BigDecimal.valueOf(-diff).multiply(TIME_BONUS_PER_SECOND).setScale(2, RoundingMode.HALF_UP);
        }

        host.setTimeBonus(hostBonus);
        guest.setTimeBonus(guestBonus);

        host.setFinalScore(clampScore(safeScore(host.getBaseScore()).add(hostBonus)));
        guest.setFinalScore(clampScore(safeScore(guest.getBaseScore()).add(guestBonus)));
    }

    private int compareFinalScore(BattleParticipantState host, BattleParticipantState guest) {
        BigDecimal hostScore = safeScore(host != null ? host.getFinalScore() : null);
        BigDecimal guestScore = safeScore(guest != null ? guest.getFinalScore() : null);
        return hostScore.compareTo(guestScore);
    }

    private int compareElapsed(BattleParticipantState host, BattleParticipantState guest) {
        long hostElapsed = effectiveElapsedSeconds(host);
        long guestElapsed = effectiveElapsedSeconds(guest);
        return Long.compare(guestElapsed, hostElapsed);
    }

    private Integer calculateWinnerElapsedMs(BattleRoomState state, Long winnerUserId) {
        if (state == null || winnerUserId == null) return null;
        BattleParticipantState winner = state.participant(winnerUserId);
        if (winner != null && winner.getElapsedSeconds() != null) {
            long millis = winner.getElapsedSeconds() * 1000L;
            return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
        }
        LocalDateTime startedAt = state.getStartedAt();
        LocalDateTime finishedAt = state.getFinishedAt();
        if (startedAt != null && finishedAt != null) {
            long millis = Duration.between(startedAt, finishedAt).toMillis();
            if (millis < 0) return null;
            return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
        }
        return null;
    }

    private long effectiveElapsedSeconds(BattleParticipantState participant) {
        if (participant == null || participant.getElapsedSeconds() == null) {
            return Long.MAX_VALUE;
        }
        return participant.getElapsedSeconds();
    }

    private BigDecimal safeScore(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal clampScore(BigDecimal value) {
        BigDecimal scaled = safeScore(value);
        if (scaled.compareTo(MAX_SCORE) > 0) {
            return MAX_SCORE;
        }
        return scaled;
    }

    private String buildJudgeSummary(BigDecimal baseScore, String message) {
        BigDecimal score = baseScore != null ? baseScore : BigDecimal.ZERO;
        String smell = scoreToSmell(score);
        String issue = extractIssueHint(message);
        if (issue == null || issue.isBlank()) {
            return smell;
        }
        return smell + ": " + issue;
    }

    private String normalizeJudgeDetail(String message) {
        if (message == null) return null;
        String normalized = message;
        normalized = normalized.replace(
                "\uC5B8\uC5B4\uB97C \uB354 \uC5F0\uC2B5\uD558\uC138\uC694",
                "\uC5B8\uC5B4\uB97C \uC6B0\uB9AC\uC0AC\uC774\uD2B8\uC758 \uC54C\uACE0\uB9AC\uC998 \uD480\uC774\uC5D0\uC11C \uB354 \uACF5\uBD80\uD574\uBCF4\uC138\uC694"
        );
        normalized = normalized.replace(
                "\uC5B8\uC5B4\uB97C \uB354 \uC5F0\uC2B5\uD558\uC138\uC694!",
                "\uC5B8\uC5B4\uB97C \uC6B0\uB9AC\uC0AC\uC774\uD2B8\uC758 \uC54C\uACE0\uB9AC\uC998 \uD480\uC774\uC5D0\uC11C \uB354 \uACF5\uBD80\uD574\uBCF4\uC138\uC694!"
        );
        normalized = normalized.replace(
                "\uC5B8\uC5B4\uB97C \uB354 \uC5F0\uC2B5\uD558\uC138\uC694.",
                "\uC5B8\uC5B4\uB97C \uC6B0\uB9AC\uC0AC\uC774\uD2B8\uC758 \uC54C\uACE0\uB9AC\uC998 \uD480\uC774\uC5D0\uC11C \uB354 \uACF5\uBD80\uD574\uBCF4\uC138\uC694."
        );
        normalized = normalized.replace(
                "\uC5B8\uC5B4\uB97C \uB354 \uC5F0\uC2B5\uD574\uBCF4\uC138\uC694",
                "\uC5B8\uC5B4\uB97C \uC6B0\uB9AC\uC0AC\uC774\uD2B8\uC758 \uC54C\uACE0\uB9AC\uC998 \uD480\uC774\uC5D0\uC11C \uB354 \uACF5\uBD80\uD574\uBCF4\uC138\uC694"
        );
        return normalized;
    }

    private String scoreToSmell(BigDecimal score) {
        BigDecimal safe = score != null ? score : BigDecimal.ZERO;
        if (safe.compareTo(new BigDecimal("10.00")) <= 0) return "\uC369\uC740 \uB0C4\uC0C8 \uC9C4\uB3D9 \uC73C~";
        if (safe.compareTo(new BigDecimal("30.00")) <= 0) return "\uACF0\uD321\uC774 \uB0C4\uC0C8";
        if (safe.compareTo(new BigDecimal("50.00")) <= 0) return "\uB045\uB045\uD55C \uB0C4\uC0C8";
        if (safe.compareTo(new BigDecimal("70.00")) <= 0) return "\uBBF8\uC9C0\uADFC\uD55C \uB0C4\uC0C8";
        if (safe.compareTo(new BigDecimal("85.00")) <= 0) return "\uAD1C\uCC2E\uC740 \uD5A5";
        if (safe.compareTo(new BigDecimal("95.00")) <= 0) return "\uD5A5\uAE43\uD55C \uB0C4\uC0C8";
        return "\uAF43\uD5A5\uAE30 \uD3ED\uBC1C";
    }

    private String extractIssueHint(String message) {
        if (message == null || message.isBlank()) return null;
        String normalized = message.replace('\r', ' ').replace('\n', ' ').toLowerCase();
        if (containsAny(normalized, "\uCEF4\uD30C\uC77C", "compile")) return "\uCEF4\uD30C\uC77C \uC624\uB958\uB85C \uC2E4\uD589 \uBD88\uAC00";
        if (containsAny(normalized, "\uC785\uB825") && containsAny(normalized, "\uC5C6", "\uB204\uB77D", "\uBC1B\uC9C0", "\uC77D\uC9C0")) {
            return "\uC785\uB825 \uCC98\uB9AC \uB204\uB77D";
        }
        if (containsAny(normalized, "\uCD9C\uB825") && containsAny(normalized, "\uC5C6", "\uB204\uB77D", "\uD615\uC2DD", "\uD2C0")) {
            return "\uCD9C\uB825 \uD615\uC2DD \uBBF8\uC900\uC218";
        }
        if (containsAny(normalized, "\uB7F0\uD0C0\uC784", "runtime", "\uC608\uC678", "exception")) return "\uB7F0\uD0C0\uC784 \uC624\uB958 \uAC00\uB2A5";
        if (containsAny(normalized, "\uC2DC\uAC04 \uCD08\uACFC", "timeout", "\uBCF5\uC7A1\uB3C4", "\uB290\uB9AC")) return "\uC2DC\uAC04/\uBCF5\uC7A1\uB3C4 \uAC1C\uC120 \uD544\uC694";
        if (containsAny(normalized, "\uC624\uB2F5", "\uD2C0\uB9BC", "\uC815\uB2F5") && containsAny(normalized, "\uBABB", "\uBD88\uAC00", "\uC2E4\uD328", "\uC544\uB2D8", "\uC54A")) {
            return "\uC815\uD655\uB3C4 \uBD80\uC871";
        }
        if (containsAny(normalized, "\uC811\uADFC", "\uC54C\uACE0\uB9AC\uC998", "\uAD6C\uD604") && containsAny(normalized, "\uBBF8\uD761", "\uBD80\uC871", "\uAC1C\uC120")) {
            return "\uC811\uADFC\uBC95 \uC7AC\uAC80\uD1A0 \uD544\uC694";
        }
        if (containsAny(normalized, "\uAE54\uB054", "\uC88B", "\uC6B0\uC218", "\uD6CC\uB96D", "\uBA85\uD655")) return "\uAD6C\uD604\uC774 \uAE54\uAC94\uD568";
        return null;
    }

    private boolean containsAny(String text, String... tokens) {
        if (text == null) return false;
        for (String token : tokens) {
            if (token != null && !token.isEmpty() && text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /* ------------------------------------------------------------
     * Hold/Refund helpers
     * ------------------------------------------------------------ */

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

        ensureNicknames(state);
        ensureActiveAndMembers(state);
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
        if (hostId != null) battleMessageService.sendErrorToUser(hostId, ex.getErrorCode(), message);
        if (guestId != null) battleMessageService.sendErrorToUser(guestId, ex.getErrorCode(), message);
    }

    /* ------------------------------------------------------------
     * Disconnect grace
     * ------------------------------------------------------------ */

    private void startDisconnectGrace(BattleRoomState state, Long userId) {
        Long opponent = Objects.equals(userId, state.getHostUserId()) ? state.getGuestUserId() : state.getHostUserId();
        if (opponent == null) {
            // 상대가 없으면 그냥 취소
            cancelRoom(state);
            return;
        }

        String key = graceKey(state.getRoomId(), userId);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) return;

        stringRedisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(15));

        ScheduledFuture<?> future = battleTaskScheduler.schedule(
                () -> finishDueToDisconnect(state.getRoomId(), opponent, userId),
                Instant.now().plusSeconds(15)
        );

        disconnectTasks.put(key, future);
        log.info("[battle] matchId={} userId={} action=disconnect-grace", state.getMatchId(), userId);
    }

    private void finishDueToDisconnect(String roomId, Long winnerUserId, Long loserUserId) {
        String key = graceKey(roomId, loserUserId);
        Boolean exists = stringRedisTemplate.hasKey(key);
        if (!Boolean.TRUE.equals(exists)) return;

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

    private void cancelRoom(BattleRoomState state) {
        state.setStatus(BattleStatus.CANCELED);
        state.setFinishedAt(BattleTime.nowKst());
        state.setWinReason("CANCELED");
        state.setWinnerUserId(null);

        if (state.isCountdownStarted()) {
            battleSettlementService.refundAll(state);
        }

        ensureNicknames(state);
        ensureActiveAndMembers(state);
        saveRoom(state);

        removeRoomFromLobby(state.getRoomId());
        battleMatchService.markCanceled(state.getMatchId());
        cancelTimeoutTask(state.getRoomId());

        battleMessageService.publishFinish(state);
        broadcastLobby();

        // 포스트게임 개념 없이 즉시 정리
        cleanupRoomKeys(state.getRoomId(), participantIds(state));

        log.info("[battle] matchId={} action=cancel state={}", state.getMatchId(), state.getStatus());
    }

    /* ------------------------------------------------------------
     * Password rate limit
     * ------------------------------------------------------------ */

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
        // 이미 enforcePasswordRateLimit에서 카운트됨
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



