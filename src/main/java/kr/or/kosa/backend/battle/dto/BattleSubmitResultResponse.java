package kr.or.kosa.backend.battle.dto;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BattleSubmitResultResponse {
    private final Long userId;
    private final Instant submittedAt;
    private final Long elapsedSeconds;
    private final BigDecimal baseScore;
    private final BigDecimal timeBonus;
    private final BigDecimal finalScore;
    private final String message;
}
