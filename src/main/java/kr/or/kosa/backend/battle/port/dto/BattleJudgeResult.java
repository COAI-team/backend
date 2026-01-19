package kr.or.kosa.backend.battle.port.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
@Builder
public class BattleJudgeResult {
    private final boolean accepted;
    private final String message;
    private final BigDecimal score;

    public static BattleJudgeResult accepted() {
        return BattleJudgeResult.builder()
                .accepted(true)
                .score(BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP))
                .message("\uCC44\uC810 \uC644\uB8CC")
                .build();
    }

    public static BattleJudgeResult rejected(String message) {
        return BattleJudgeResult.builder()
                .accepted(false)
                .score(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .message(message)
                .build();
    }
}
