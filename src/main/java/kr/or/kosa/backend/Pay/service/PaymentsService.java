package kr.or.kosa.backend.Pay.service;

import kr.or.kosa.backend.Pay.entity.Payments;
import kr.or.kosa.backend.Pay.entity.Subscription;

import java.util.List;
import java.util.Optional;

public interface PaymentsService {

    // 1. 결제 정보 초기 저장 (사용자 위젯 오픈 전)
    Payments savePayment(Payments payments);

    // 2. 결제 승인 및 DB 업데이트 (토스 API 호출 포함)
    Payments confirmAndSavePayment(String paymentKey, String orderId, Long amount);

    // 3. 주문 ID로 조회
    Optional<Payments> getPaymentByOrderId(String orderId);

    // 사용자의 활성화된 구독 목록 조회
    List<Subscription> getActiveSubscriptions(String userId);

    // **[추가]** 결제 취소 및 환불 처리
    Payments cancelPayment(String paymentKey, String cancelReason);
}