package kr.or.kosa.backend.battle.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class BattleRoomState implements Serializable {
    private String roomId;
    private String matchId;
    private String title;
    private BattleStatus status;
    private Long hostUserId;
    private Long guestUserId;
    private Long algoProblemId;
    private boolean randomProblem;
    private Long languageId;
    private String levelMode;
    private BigDecimal betAmount;
    private Integer maxDurationMinutes;
    private boolean countdownStarted;
    private boolean isPrivate;
    private String passwordHash;

    // KST LocalDateTime only
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime postGameUntil;
    private LocalDateTime readyCooldownUntil;

    private Long winnerUserId;
    private String winReason;
    private Map<Long, BattleParticipantState> participants = new HashMap<>();

    public BattleRoomState() {
    }

    public BattleParticipantState participant(Long userId) {
        if (userId == null || participants == null) {
            return null;
        }
        BattleParticipantState direct = participants.get(userId);
        if (direct != null) {
            return direct;
        }
        for (BattleParticipantState candidate : participants.values()) {
            if (candidate != null && Objects.equals(candidate.getUserId(), userId)) {
                return candidate;
            }
        }
        return null;
    }

    public void addOrUpdateParticipant(BattleParticipantState participant) {
        if (participants == null) {
            participants = new HashMap<>();
        }
        participants.put(participant.getUserId(), participant);
    }

    public static BattleRoomStateBuilder builder() {
        return new BattleRoomStateBuilder();
    }

    public static class BattleRoomStateBuilder {
        private String roomId;
        private String matchId;
        private String title;
        private BattleStatus status;
        private Long hostUserId;
        private Long guestUserId;
        private Long algoProblemId;
        private boolean randomProblem;
        private Long languageId;
        private String levelMode;
        private BigDecimal betAmount;
        private Integer maxDurationMinutes;
        private boolean countdownStarted;
        private boolean isPrivate;
        private String passwordHash;

        private LocalDateTime createdAt;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private LocalDateTime postGameUntil;
        private LocalDateTime readyCooldownUntil;

        private Long winnerUserId;
        private String winReason;
        private Map<Long, BattleParticipantState> participants = new HashMap<>();

        public BattleRoomStateBuilder roomId(String roomId) { this.roomId = roomId; return this; }
        public BattleRoomStateBuilder matchId(String matchId) { this.matchId = matchId; return this; }
        public BattleRoomStateBuilder title(String title) { this.title = title; return this; }
        public BattleRoomStateBuilder status(BattleStatus status) { this.status = status; return this; }
        public BattleRoomStateBuilder hostUserId(Long hostUserId) { this.hostUserId = hostUserId; return this; }
        public BattleRoomStateBuilder guestUserId(Long guestUserId) { this.guestUserId = guestUserId; return this; }
        public BattleRoomStateBuilder algoProblemId(Long algoProblemId) { this.algoProblemId = algoProblemId; return this; }
        public BattleRoomStateBuilder randomProblem(boolean randomProblem) { this.randomProblem = randomProblem; return this; }
        public BattleRoomStateBuilder languageId(Long languageId) { this.languageId = languageId; return this; }
        public BattleRoomStateBuilder levelMode(String levelMode) { this.levelMode = levelMode; return this; }
        public BattleRoomStateBuilder betAmount(BigDecimal betAmount) { this.betAmount = betAmount; return this; }
        public BattleRoomStateBuilder maxDurationMinutes(Integer maxDurationMinutes) { this.maxDurationMinutes = maxDurationMinutes; return this; }
        public BattleRoomStateBuilder countdownStarted(boolean countdownStarted) { this.countdownStarted = countdownStarted; return this; }
        public BattleRoomStateBuilder isPrivate(boolean isPrivate) { this.isPrivate = isPrivate; return this; }
        public BattleRoomStateBuilder passwordHash(String passwordHash) { this.passwordHash = passwordHash; return this; }

        public BattleRoomStateBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public BattleRoomStateBuilder startedAt(LocalDateTime startedAt) { this.startedAt = startedAt; return this; }
        public BattleRoomStateBuilder finishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; return this; }
        public BattleRoomStateBuilder postGameUntil(LocalDateTime postGameUntil) { this.postGameUntil = postGameUntil; return this; }
        public BattleRoomStateBuilder readyCooldownUntil(LocalDateTime readyCooldownUntil) { this.readyCooldownUntil = readyCooldownUntil; return this; }

        public BattleRoomStateBuilder winnerUserId(Long winnerUserId) { this.winnerUserId = winnerUserId; return this; }
        public BattleRoomStateBuilder winReason(String winReason) { this.winReason = winReason; return this; }
        public BattleRoomStateBuilder participants(Map<Long, BattleParticipantState> participants) { this.participants = participants; return this; }

        public BattleRoomState build() {
            BattleRoomState state = new BattleRoomState();
            state.setRoomId(roomId);
            state.setMatchId(matchId);
            state.setTitle(title);
            state.setStatus(status);
            state.setHostUserId(hostUserId);
            state.setGuestUserId(guestUserId);
            state.setAlgoProblemId(algoProblemId);
            state.setRandomProblem(randomProblem);
            state.setLanguageId(languageId);
            state.setLevelMode(levelMode);
            state.setBetAmount(betAmount);
            state.setMaxDurationMinutes(maxDurationMinutes);
            state.setCountdownStarted(countdownStarted);
            state.setPrivate(isPrivate);
            state.setPasswordHash(passwordHash);

            state.setCreatedAt(createdAt);
            state.setStartedAt(startedAt);
            state.setFinishedAt(finishedAt);
            state.setPostGameUntil(postGameUntil);
            state.setReadyCooldownUntil(readyCooldownUntil);

            state.setWinnerUserId(winnerUserId);
            state.setWinReason(winReason);
            state.setParticipants(participants != null ? participants : new HashMap<>());
            return state;
        }
    }

    // getters/setters
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getMatchId() { return matchId; }
    public void setMatchId(String matchId) { this.matchId = matchId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public BattleStatus getStatus() { return status; }
    public void setStatus(BattleStatus status) { this.status = status; }
    public Long getHostUserId() { return hostUserId; }
    public void setHostUserId(Long hostUserId) { this.hostUserId = hostUserId; }
    public Long getGuestUserId() { return guestUserId; }
    public void setGuestUserId(Long guestUserId) { this.guestUserId = guestUserId; }
    public Long getAlgoProblemId() { return algoProblemId; }
    public void setAlgoProblemId(Long algoProblemId) { this.algoProblemId = algoProblemId; }
    public boolean isRandomProblem() { return randomProblem; }
    public void setRandomProblem(boolean randomProblem) { this.randomProblem = randomProblem; }
    public Long getLanguageId() { return languageId; }
    public void setLanguageId(Long languageId) { this.languageId = languageId; }
    public String getLevelMode() { return levelMode; }
    public void setLevelMode(String levelMode) { this.levelMode = levelMode; }
    public BigDecimal getBetAmount() { return betAmount; }
    public void setBetAmount(BigDecimal betAmount) { this.betAmount = betAmount; }
    public Integer getMaxDurationMinutes() { return maxDurationMinutes; }
    public void setMaxDurationMinutes(Integer maxDurationMinutes) { this.maxDurationMinutes = maxDurationMinutes; }
    public boolean isCountdownStarted() { return countdownStarted; }
    public void setCountdownStarted(boolean countdownStarted) { this.countdownStarted = countdownStarted; }
    public boolean isPrivate() { return isPrivate; }
    public void setPrivate(boolean aPrivate) { this.isPrivate = aPrivate; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public LocalDateTime getPostGameUntil() { return postGameUntil; }
    public void setPostGameUntil(LocalDateTime postGameUntil) { this.postGameUntil = postGameUntil; }
    public LocalDateTime getReadyCooldownUntil() { return readyCooldownUntil; }
    public void setReadyCooldownUntil(LocalDateTime readyCooldownUntil) { this.readyCooldownUntil = readyCooldownUntil; }

    public Long getWinnerUserId() { return winnerUserId; }
    public void setWinnerUserId(Long winnerUserId) { this.winnerUserId = winnerUserId; }
    public String getWinReason() { return winReason; }
    public void setWinReason(String winReason) { this.winReason = winReason; }
    public Map<Long, BattleParticipantState> getParticipants() { return participants; }
    public void setParticipants(Map<Long, BattleParticipantState> participants) { this.participants = participants; }
}
