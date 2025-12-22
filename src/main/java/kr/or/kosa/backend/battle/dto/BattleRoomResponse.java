package kr.or.kosa.backend.battle.dto;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import kr.or.kosa.backend.battle.domain.BattleParticipantState;
import kr.or.kosa.backend.battle.domain.BattleRoomState;
import kr.or.kosa.backend.battle.domain.BattleStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BattleRoomResponse {
    private final String roomId;
    private final String matchId;
    private final String title;
    private final BattleStatus status;
    private final Long hostUserId;
    private final Long guestUserId;
    private final String hostNickname;
    private final String guestNickname;
    private final Long algoProblemId;
    private final Long languageId;
    private final String levelMode;
    private final BigDecimal betAmount;
    private final Integer maxDurationMinutes;
    private final boolean countdownStarted;
    private final boolean isPrivate;
    private final String createdAt;
    private final String startedAt;
    private final String finishedAt;
    private final String postGameUntil;
    private final Long winnerUserId;
    private final String winReason;
    private final Map<Long, BattleParticipantResponse> participants;

    public static BattleRoomResponse from(BattleRoomState state) {
        Map<Long, BattleParticipantResponse> participantSnapshots = new HashMap<>();
        Optional.ofNullable(state.getParticipants())
                .orElseGet(Map::of)
                .values()
                .forEach(it -> {
                    if (it != null && it.getUserId() != null) {
                        BattleParticipantResponse resp = BattleParticipantResponse.from(it);
                        if (resp.getNickname() == null || resp.getNickname().isBlank()) {
                            resp = BattleParticipantResponse.builder()
                                    .userId(resp.getUserId())
                                    .nickname("사용자#" + resp.getUserId())
                                    .ready(resp.isReady())
                                    .surrendered(resp.isSurrendered())
                                    .finished(resp.isFinished())
                                    .lastSubmittedAt(resp.getLastSubmittedAt())
                                    .elapsedSeconds(resp.getElapsedSeconds())
                                    .baseScore(resp.getBaseScore())
                                    .timeBonus(resp.getTimeBonus())
                                    .finalScore(resp.getFinalScore())
                                    .build();
                        }
                        participantSnapshots.put(it.getUserId(), resp);
                    }
                });

        String hostNickname = Optional.ofNullable(state.getHostUserId())
                .map(participantSnapshots::get)
                .map(BattleParticipantResponse::getNickname)
                .orElse(state.getHostUserId() != null ? "사용자#" + state.getHostUserId() : null);
        String guestNickname = Optional.ofNullable(state.getGuestUserId())
                .map(participantSnapshots::get)
                .map(BattleParticipantResponse::getNickname)
                .orElse(state.getGuestUserId() != null ? "사용자#" + state.getGuestUserId() : null);

        return BattleRoomResponse.builder()
                .roomId(state.getRoomId())
                .matchId(state.getMatchId())
                .title(state.getTitle())
                .status(state.getStatus())
                .hostUserId(state.getHostUserId())
                .guestUserId(state.getGuestUserId())
                .hostNickname(hostNickname)
                .guestNickname(guestNickname)
                .algoProblemId(state.getAlgoProblemId())
                .languageId(state.getLanguageId())
                .levelMode(state.getLevelMode())
                .betAmount(state.getBetAmount())
                .maxDurationMinutes(state.getMaxDurationMinutes())
                .countdownStarted(state.isCountdownStarted())
                .isPrivate(state.isPrivate())
                .createdAt(toIsoString(state.getCreatedAt()))
                .startedAt(toIsoString(state.getStartedAt()))
                .finishedAt(toIsoString(state.getFinishedAt()))
                .postGameUntil(toIsoString(state.getPostGameUntil()))
                .winnerUserId(state.getWinnerUserId())
                .winReason(state.getWinReason())
                .participants(participantSnapshots)
                .build();
    }

    private static String toIsoString(java.time.Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
