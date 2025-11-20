package kr.or.kosa.backend.Pay.entity;

//import jakarta.persistence.Entity;
//import jakarta.persistence.Id;
//import jakarta.persistence.Table;
import lombok.*;

// JPA 어노테이션은 DB 테이블 정의를 위해 유지 (MyBatis가 사용하지는 않음)
//@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@ToString
//@Table(name = "payments")
public class Payments {

    //@Id // Primary Key 명시
    private String orderId;
    private String paymentKey;
    private String orderName;
    private String userId;
    private String customerName;
    private int amount;
    private String status; // READY, DONE, CANCELED
    private String requestedAt; // 결제 요청/생성일자
}