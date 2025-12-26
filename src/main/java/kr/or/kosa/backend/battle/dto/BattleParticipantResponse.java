package kr.or.kosa.backend.battle.dto;

import kr.or.kosa.backend.battle.domain.BattleParticipantState;
import kr.or.kosa.backend.battle.util.BattleTime;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class BattleParticipantResponse {
    private final Long userId;
    private final String nickname;
    private final Integer grade;
    private final boolean ready;
    private final boolean surrendered;
    private final boolean finished;
    private final String lastSubmittedAt; // ISO(+09:00)
    private final Long elapsedSeconds;
    private final BigDecimal baseScore;
    private final BigDecimal timeBonus;
    private final BigDecimal finalScore;
    private final BigDecimal pointBalance;
    private final String judgeMessage;

    public static BattleParticipantResponse from(BattleParticipantState state) {
        if (state == null) {
            return null;
        }
        return BattleParticipantResponse.builder()
                .userId(state.getUserId())
                .nickname(state.getNickname())
                .grade(state.getGrade())
                .ready(state.isReady())
                .surrendered(state.isSurrendered())
                .finished(state.isFinished())
                .lastSubmittedAt(toKstIsoString(state.getLastSubmittedAt()))
                .elapsedSeconds(state.getElapsedSeconds())
                .baseScore(state.getBaseScore())
                .timeBonus(state.getTimeBonus())
                .finalScore(state.getFinalScore())
                .pointBalance(state.getPointBalance())
                .judgeMessage(state.getJudgeMessage())
                .build();
    }

    private static String toKstIsoString(LocalDateTime ldt) {
        return ldt == null ? null : BattleTime.toZonedKst(ldt).toOffsetDateTime().toString();
    }
}
