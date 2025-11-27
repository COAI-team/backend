package kr.or.kosa.backend.pay.repository;

import kr.or.kosa.backend.pay.entity.Payments;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PaymentsMapper {

    // INSERT (READY 단계 최초 저장)
    void insertPayment(Payments payments);

    // orderId로 단건 조회
    Optional<Payments> findPaymentByOrderId(@Param("orderId") String orderId);

    // paymentKey로 단건 조회 (주로 환불용, DONE 상태만)
    Optional<Payments> findPaymentByPaymentKey(@Param("paymentKey") String paymentKey);

    // 승인 시 상태/키/카드 정보 업데이트
    int updatePaymentStatus(Payments payments);

    // 취소 시 상태/canceled_at 갱신
    int updatePaymentStatusToCanceled(@Param("orderId") String orderId,
                                      @Param("status") String status);

    // READY 단계에서 기존 orderId 결제정보 덮어쓰기
    int updatePaymentForReady(Payments payments);

    // 최근 결제 N건 (환불 남용 체크)
    List<Payments> findRecentPaymentsByUser(@Param("userId") String userId,
                                            @Param("limit") int limit);
}
