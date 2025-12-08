package kr.or.kosa.backend.pay.service;

import kr.or.kosa.backend.pay.dto.UpgradeQuoteResponse;
import kr.or.kosa.backend.pay.entity.Payments;
import kr.or.kosa.backend.pay.entity.Subscription;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PaymentsService {

    // 1. 결제 준비(READY)/포인트 전액 결제 처리
    Payments savePayment(Payments payments);

    // 2. 결제 승인 → DB 업데이트 (토스 API 호출 포함)
    Payments confirmAndSavePayment(Long userId, String paymentKey, String orderId, Long amount);

    // 3. 주문 ID로 조회
    Optional<Payments> getPaymentByOrderId(String orderId);

    // 내 활성/과거 구독 목록 조회
    List<Subscription> getActiveSubscriptions(Long userId);

    // 4. 결제 취소 → 환불 처리
    Payments cancelPayment(Long userId, String paymentKey, String cancelReason);

    // BASIC → PRO 업그레이드 추가 결제 금액 계산
    UpgradeQuoteResponse getUpgradeQuote(Long userId, String targetPlanCode);

    // 결제/취소 히스토리 조회 (기간/상태 필터)
    List<Payments> getPaymentHistory(Long userId, LocalDate from, LocalDate to, String status);
}
