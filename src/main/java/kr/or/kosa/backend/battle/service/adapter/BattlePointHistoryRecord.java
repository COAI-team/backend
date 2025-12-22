package kr.or.kosa.backend.battle.service.adapter;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class BattlePointHistoryRecord {
    private Long id;
    private Long userId;
    private BigDecimal changeAmount;
    private String type;
    private String paymentOrderId;
    private String description;
    private String battleMatchId;
}
