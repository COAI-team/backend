package kr.or.kosa.backend.pay.entity;

import lombok.*;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@ToString
public class SubscriptionPlan {
    private String planCode;
    private String name;
    private BigDecimal monthlyFee;  // INT → BigDecimal
    private boolean active;
}
