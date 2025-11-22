package kr.or.kosa.backend.pay.entity;

import lombok.*;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@ToString
public class PointHistory {

    private Long id;               // AUTO_INCREMENT PK
    private String userId;
    private int changeAmount;      // + 적립, - 사용
    private String type;           // EARN / USE / REFUND 등
    private String paymentOrderId; // 어느 결제와 묶였는지 (nullable)
    private String description;    // 설명
    private LocalDateTime createdAt;
}
