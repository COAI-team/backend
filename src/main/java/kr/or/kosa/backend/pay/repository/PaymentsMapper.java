package kr.or.kosa.backend.pay.repository;


import kr.or.kosa.backend.pay.entity.Payments;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface PaymentsMapper {

    // 1. 결제 정보 초기 저장 (결제 요청 직후)
    int insertPayment(Payments payments);

    // 2. 주문 ID로 결제 정보 조회 (승인 전/후 상태 확인용)
    Optional<Payments> findPaymentByOrderId(@Param("orderId") String orderId);

    // 3. 결제 승인 후 상태 및 paymentKey 업데이트
    int updatePaymentStatus(Payments payments);

    // **[추가]** paymentKey로 결제 정보 조회 (환불 시 필요)
    Optional<Payments> findPaymentByPaymentKey(@Param("paymentKey") String paymentKey);

    // **[추가]** 환불/취소 상태로 업데이트
    int updatePaymentStatusToCanceled(@Param("orderId") String orderId, @Param("status") String status);
}