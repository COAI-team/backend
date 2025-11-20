package kr.or.kosa.backend.Pay.entity;

import lombok.*;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@ToString
// 이 객체는 'subscriptions' 테이블과 매핑됩니다.
public class Subscription {

    private Long subscriptionId;
    private String userId;
    private String orderId;      // Payments와 연결
    private String subscriptionType;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String status;           // ACTIVE, EXPIRED, CANCELED
}