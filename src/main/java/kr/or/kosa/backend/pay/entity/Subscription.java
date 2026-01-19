package kr.or.kosa.backend.pay.entity;

import lombok.*;
import java.time.LocalDateTime;

/**
 * subscriptions 테이블과 매핑되는 VO (MyBatis용)
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@ToString
public class Subscription {

    private Long subscriptionId;
    private Long userId;
    private String orderId;          // Payments.orderId 와 연결
    private String subscriptionType; // FREE / BASIC / PRO 등
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String status;           // ACTIVE, EXPIRED, CANCELED
}
