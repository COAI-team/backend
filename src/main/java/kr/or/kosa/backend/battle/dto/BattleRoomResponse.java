package kr.or.kosa.backend.battle.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import kr.or.kosa.backend.battle.domain.BattleRoomState;
import kr.or.kosa.backend.battle.domain.BattleStatus;
import kr.or.kosa.backend.battle.util.BattleTime;
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
    private final boolean randomProblem;
    private final Long languageId;
    private final String levelMode;
    private final BigDecimal betAmount;
    private final Integer maxDurationMinutes;
    private final boolean countdownStarted;
    private final boolean isPrivate;

    private final String createdAt;     // ISO(+09:00)
    private final String startedAt;     // ISO(+09:00)
    private final String finishedAt;    // ISO(+09:00)
    private final String postGameUntil; // ISO(+09:00)
    private final String readyCooldownUntil; // ISO(+09:00)

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
                                    .nickname("\uC0AC\uC6A9\uC790#" + resp.getUserId())
                                    .grade(resp.getGrade())
                                    .ready(resp.isReady())
                                    .surrendered(resp.isSurrendered())
                                    .finished(resp.isFinished())
                                    .lastSubmittedAt(resp.getLastSubmittedAt())
                                    .elapsedSeconds(resp.getElapsedSeconds())
                                    .baseScore(resp.getBaseScore())
                                    .timeBonus(resp.getTimeBonus())
                                    .finalScore(resp.getFinalScore())
                                    .pointBalance(resp.getPointBalance())
                                    .build();
                        }

                        participantSnapshots.put(it.getUserId(), resp);
                    }
                });

        String hostNickname = Optional.ofNullable(state.getHostUserId())
                .map(participantSnapshots::get)
                .map(BattleParticipantResponse::getNickname)
                .orElse(state.getHostUserId() != null ? "\uC0AC\uC6A9\uC790#" + state.getHostUserId() : null);

        String guestNickname = Optional.ofNullable(state.getGuestUserId())
                .map(participantSnapshots::get)
                .map(BattleParticipantResponse::getNickname)
                .orElse(state.getGuestUserId() != null ? "\uC0AC\uC6A9\uC790#" + state.getGuestUserId() : null);

        boolean maskProblem = state.isRandomProblem()
                && (state.getStatus() == BattleStatus.WAITING || state.getStatus() == BattleStatus.COUNTDOWN);
        Long visibleProblemId = maskProblem ? null : state.getAlgoProblemId();

        return BattleRoomResponse.builder()
                .roomId(state.getRoomId())
                .matchId(state.getMatchId())
                .title(state.getTitle())
                .status(state.getStatus())
                .hostUserId(state.getHostUserId())
                .guestUserId(state.getGuestUserId())
                .hostNickname(hostNickname)
                .guestNickname(guestNickname)
                .algoProblemId(visibleProblemId)
                .randomProblem(state.isRandomProblem())
                .languageId(state.getLanguageId())
                .levelMode(state.getLevelMode())
                .betAmount(state.getBetAmount())
                .maxDurationMinutes(state.getMaxDurationMinutes())
                .countdownStarted(state.isCountdownStarted())
                .isPrivate(state.isPrivate())
                .createdAt(toKstIsoString(state.getCreatedAt()))
                .startedAt(toKstIsoString(state.getStartedAt()))
                .finishedAt(toKstIsoString(state.getFinishedAt()))
                .postGameUntil(toKstIsoString(state.getPostGameUntil()))
                .readyCooldownUntil(toKstIsoString(state.getReadyCooldownUntil()))
                .winnerUserId(state.getWinnerUserId())
                .winReason(state.getWinReason())
                .participants(participantSnapshots)
                .build();
    }

    private static String toKstIsoString(LocalDateTime ldt) {
        return ldt == null ? null : BattleTime.toZonedKst(ldt).toOffsetDateTime().toString();
    }
}
