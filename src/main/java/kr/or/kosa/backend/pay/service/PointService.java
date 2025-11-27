package kr.or.kosa.backend.pay.service;

import java.math.BigDecimal;

public interface PointService {

    // 사용 전에 잔액 체크
    void validatePointBalance(String userId, BigDecimal amountToUse);

    // 실제 사용 (차감)
    void usePoint(String userId, BigDecimal amountToUse, String orderId);

    // 환불 시 되돌리기
    void refundPoint(String userId, BigDecimal amountToRefund, String orderId, String reason);
}
