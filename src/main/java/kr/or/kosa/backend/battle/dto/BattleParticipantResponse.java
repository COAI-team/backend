package kr.or.kosa.backend.battle.dto;

import kr.or.kosa.backend.battle.domain.BattleParticipantState;
import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class BattleParticipantResponse {
    private final Long userId;
    private final String nickname;
    private final boolean ready;
    private final boolean surrendered;
    private final boolean finished;
    private final String lastSubmittedAt;
    private final Long elapsedSeconds;
    private final BigDecimal baseScore;
    private final BigDecimal timeBonus;
    private final BigDecimal finalScore;
    private final BigDecimal pointBalance;

    public static BattleParticipantResponse from(BattleParticipantState state) {
        if (state == null) {
            return null;
        }
        return BattleParticipantResponse.builder()
                .userId(state.getUserId())
                .nickname(state.getNickname())
                .ready(state.isReady())
                .surrendered(state.isSurrendered())
                .finished(state.isFinished())
                .lastSubmittedAt(toIso(state.getLastSubmittedAt()))
                .elapsedSeconds(state.getElapsedSeconds())
                .baseScore(state.getBaseScore())
                .timeBonus(state.getTimeBonus())
                .finalScore(state.getFinalScore())
                .pointBalance(state.getPointBalance())
                .build();
    }

    private static String toIso(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
