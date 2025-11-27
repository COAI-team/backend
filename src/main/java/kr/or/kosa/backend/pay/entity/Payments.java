package kr.or.kosa.backend.pay.entity;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@ToString
public class Payments {

    // 주문 ID (PK 역할)
    private String orderId;

    // 실제 로그인한 유저의 PK(아이디 등)
    private String userId;

    // 구독 플랜 코드 (FREE / BASIC / PRO 등)
    private String planCode;

    // Toss 결제 키
    private String paymentKey;

    // 화면에 보여줄 주문 이름 (예: "Basic 구독 1개월")
    private String orderName;

    // 고객 이름 (표시용)
    private String customerName;

    // 원래 플랜 가격 (포인트 적용 전 금액)
    private BigDecimal originalAmount;

    // 사용한 포인트 (돈처럼 취급)
    private BigDecimal usedPoint;

    // 최종 결제 금액 (원)
    private BigDecimal amount;

    // 결제 상태: READY, DONE, CANCELED 등
    private String status;

    // 결제 요청/생성 일시
    private LocalDateTime requestedAt;

    // 결제 취소 일시
    private LocalDateTime canceledAt;

    // 결제수단: CARD / EASY_KAKAOPAY / ACCOUNT_TRANSFER / ...
    private String payMethod;

    // 토스 응답 전체 JSON
    private String pgRawResponse;

    // 카드사, 승인번호, 승인시각
    private String cardCompany;        // 예: "KB", "HYUNDAI" 등 (issuerCode 기준)
    private String cardApprovalNo;     // approveNo
    private LocalDateTime approvedAt;  // 승인 시각
}
