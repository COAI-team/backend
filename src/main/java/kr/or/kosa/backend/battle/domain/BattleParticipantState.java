package kr.or.kosa.backend.battle.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BattleParticipantState implements Serializable {
    private Long userId;
    private String nickname;
    private Integer grade;
    private boolean ready;
    private boolean surrendered;
    private boolean finished;

    // KST LocalDateTime
    private LocalDateTime lastSubmittedAt;

    private Long elapsedSeconds;
    private BigDecimal baseScore;
    private BigDecimal timeBonus;
    private BigDecimal finalScore;
    private BigDecimal pointBalance;
    private String judgeMessage;
    private Integer judgeErrorCount;
}
