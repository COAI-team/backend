package kr.or.kosa.backend.Pay.dto;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@ToString
public class PaymentsDTO {
    String orderId;
    String orderName;
    String customerName;
    int amount;
}
