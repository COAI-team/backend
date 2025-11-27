package kr.or.kosa.backend.pay.dto;

import lombok.*;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@ToString
public class PaymentsDTO {

    private String orderId;
    private String orderName;
    private String customerName;

    // 결제 금액 (BigDecimal로 통일)
    private BigDecimal amount;
}
