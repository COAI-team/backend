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
public class BattleMatch {
    private String matchId;
    private BattleStatus status;
    private Long hostUserId;
    private Long guestUserId;
    private Long algoProblemId;
    private Long languageId;
    private String levelMode;
    private String hostLevelSnapshot;
    private String guestLevelSnapshot;
    private String roomTitle;
    private BigDecimal betAmount;
    private Integer maxDurationMinutes;
    private LocalDateTime countdownStartedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime hostAcAt;
    private LocalDateTime guestAcAt;
    private Long winnerUserId;
    private String winReason;
    private Integer winnerElapsedMs;
    private BattleSettlementStatus settlementStatus;
}
