package kr.or.kosa.backend.battle.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.time.Instant;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import kr.or.kosa.backend.battle.jackson.InstantIsoDeserializer;
import kr.or.kosa.backend.battle.jackson.InstantIsoSerializer;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BattleRoomState implements Serializable {
    private String roomId;
    private String matchId;
    private String title;
    private BattleStatus status;
    private Long hostUserId;
    private Long guestUserId;
    private Long algoProblemId;
    private Long languageId;
    private String levelMode;
    private BigDecimal betAmount;
    private Integer maxDurationMinutes;
    private boolean countdownStarted;
    private boolean isPrivate;
    private String passwordHash;
    @JsonSerialize(using = InstantIsoSerializer.class)
    @JsonDeserialize(using = InstantIsoDeserializer.class)
    private Instant createdAt;
    @JsonSerialize(using = InstantIsoSerializer.class)
    @JsonDeserialize(using = InstantIsoDeserializer.class)
    private Instant startedAt;
    @JsonSerialize(using = InstantIsoSerializer.class)
    @JsonDeserialize(using = InstantIsoDeserializer.class)
    private Instant finishedAt;
    @JsonSerialize(using = InstantIsoSerializer.class)
    @JsonDeserialize(using = InstantIsoDeserializer.class)
    private Instant postGameUntil;
    private Long winnerUserId;
    private String winReason;
    private Map<Long, BattleParticipantState> participants = new HashMap<>();

    public BattleParticipantState participant(Long userId) {
        return participants.get(userId);
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
        private Long languageId;
        private String levelMode;
        private BigDecimal betAmount;
        private Integer maxDurationMinutes;
        private boolean countdownStarted;
        private boolean isPrivate;
        private String passwordHash;
        private Instant createdAt;
        private Instant startedAt;
        private Instant finishedAt;
        private Instant postGameUntil;
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
        public BattleRoomStateBuilder languageId(Long languageId) { this.languageId = languageId; return this; }
        public BattleRoomStateBuilder levelMode(String levelMode) { this.levelMode = levelMode; return this; }
        public BattleRoomStateBuilder betAmount(BigDecimal betAmount) { this.betAmount = betAmount; return this; }
        public BattleRoomStateBuilder maxDurationMinutes(Integer maxDurationMinutes) { this.maxDurationMinutes = maxDurationMinutes; return this; }
        public BattleRoomStateBuilder countdownStarted(boolean countdownStarted) { this.countdownStarted = countdownStarted; return this; }
        public BattleRoomStateBuilder isPrivate(boolean isPrivate) { this.isPrivate = isPrivate; return this; }
        public BattleRoomStateBuilder passwordHash(String passwordHash) { this.passwordHash = passwordHash; return this; }
        public BattleRoomStateBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public BattleRoomStateBuilder startedAt(Instant startedAt) { this.startedAt = startedAt; return this; }
        public BattleRoomStateBuilder finishedAt(Instant finishedAt) { this.finishedAt = finishedAt; return this; }
        public BattleRoomStateBuilder postGameUntil(Instant postGameUntil) { this.postGameUntil = postGameUntil; return this; }
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
            state.setWinnerUserId(winnerUserId);
            state.setWinReason(winReason);
            state.setParticipants(participants != null ? participants : new HashMap<>());
            return state;
        }
    }

    // Manual getters/setters to guard against annotation processing issues
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
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public Instant getPostGameUntil() { return postGameUntil; }
    public void setPostGameUntil(Instant postGameUntil) { this.postGameUntil = postGameUntil; }
    public Long getWinnerUserId() { return winnerUserId; }
    public void setWinnerUserId(Long winnerUserId) { this.winnerUserId = winnerUserId; }
    public String getWinReason() { return winReason; }
    public void setWinReason(String winReason) { this.winReason = winReason; }
    public Map<Long, BattleParticipantState> getParticipants() { return participants; }
    public void setParticipants(Map<Long, BattleParticipantState> participants) { this.participants = participants; }
}
