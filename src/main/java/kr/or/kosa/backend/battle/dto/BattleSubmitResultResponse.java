package kr.or.kosa.backend.battle.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BattleSubmitResultResponse {
    private final Long userId;
    private final String submittedAt; // ISO(+09:00)로 내려주기
    private final Long elapsedSeconds;
    private final BigDecimal baseScore;
    private final BigDecimal timeBonus;
    private final BigDecimal finalScore;
    private final String message;
    private final String judgeSummary;
    private final String judgeDetail;
    private final Boolean accepted;
}
