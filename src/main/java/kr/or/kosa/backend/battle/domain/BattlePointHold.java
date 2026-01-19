package kr.or.kosa.backend.battle.domain;

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
public class BattlePointHold {
    private String matchId;
    private Long userId;
    private BigDecimal amount;
    private BattleHoldStatus status;
    private Long pointHistoryId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
