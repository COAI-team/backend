package kr.or.kosa.backend.battle.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import kr.or.kosa.backend.battle.domain.BattleMatch;
import kr.or.kosa.backend.battle.domain.BattleParticipantState;
import kr.or.kosa.backend.battle.domain.BattleRoomState;
import kr.or.kosa.backend.battle.util.BattleTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BattleFinishResponse {
    private final String matchId;
    private final BattleFinishParticipantResponse host;
    private final BattleFinishParticipantResponse guest;
    private final Long winnerUserId;
    private final String winReason;
    private final BigDecimal betAmount;
    private final String settlementStatus;
    private final String postGameUntil; // ISO 문자열(+09:00)

    public static BattleFinishResponse from(BattleRoomState state, BattleMatch match) {
        BattleFinishParticipantResponse host = buildParticipant(state, state != null ? state.getHostUserId() : null);
        BattleFinishParticipantResponse guest = buildParticipant(state, state != null ? state.getGuestUserId() : null);

        String settlement = match != null && match.getSettlementStatus() != null
                ? match.getSettlementStatus().name()
                : null;

        return BattleFinishResponse.builder()
                .matchId(state != null ? state.getMatchId() : null)
                .host(host)
                .guest(guest)
                .winnerUserId(state != null ? state.getWinnerUserId() : null)
                .winReason(state != null ? state.getWinReason() : null)
                .betAmount(state != null ? state.getBetAmount() : null)
                .settlementStatus(settlement)
                .postGameUntil(toKstIsoString(state != null ? state.getPostGameUntil() : null))
                .build();
    }

    private static BattleFinishParticipantResponse buildParticipant(BattleRoomState state, Long userId) {
        if (state == null || userId == null) {
            return null;
        }

        BattleParticipantState participant = state.participant(userId);

        String nickname = participant != null ? participant.getNickname() : null;
        if (nickname == null || nickname.isBlank()) {
            nickname = "사용자#" + userId;
        }

        Long elapsedMs = 0L;
        if (participant != null && participant.getElapsedSeconds() != null) {
            elapsedMs = participant.getElapsedSeconds() * 1000L;
        }

        BigDecimal base = safeScore(participant != null ? participant.getBaseScore() : null);
        BigDecimal bonus = safeScore(participant != null ? participant.getTimeBonus() : null);
        BigDecimal fin = safeScore(participant != null ? participant.getFinalScore() : null);

        return BattleFinishParticipantResponse.builder()
                .userId(userId)
                .nickname(nickname)
                .grade(participant != null ? participant.getGrade() : null)
                .baseScore(base)
                .bonusScore(bonus)
                .finalScore(fin)
                .elapsedMs(elapsedMs)
                .judgeMessage(participant != null ? participant.getJudgeMessage() : null)
                .build();
    }

    private static BigDecimal safeScore(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String toKstIsoString(LocalDateTime ldt) {
        // 예: 2025-12-23T16:38:34.123+09:00
        return ldt == null ? null : BattleTime.toZonedKst(ldt).toOffsetDateTime().toString();
    }
}
