package kr.or.kosa.backend.battle.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BattleFinishParticipantResponse {
    private Long userId;
    private String nickname;
    private Integer grade;
    private Boolean solved;
    private Boolean isWinner;

    private BigDecimal baseAmount;
    private BigDecimal bonusAmount;
    private BigDecimal totalChange;
    private BigDecimal pointBalance;
    private Integer elapsedSeconds;

    private BigDecimal baseScore;
    private BigDecimal bonusScore;
    private BigDecimal finalScore;
    private Long elapsedMs;
    private String judgeMessage;
}
