package kr.or.kosa.backend.pay.service;

import java.math.BigDecimal;

public interface PointService {

    // 사용 전에 잔액 체크
    void validatePointBalance(Long userId, BigDecimal amountToUse);

    // 실제 사용 (차감)
    void usePoint(Long userId, BigDecimal amountToUse, String orderId);

    // 환불 시 되돌리기
    void refundPoint(Long userId, BigDecimal amountToRefund, String orderId, String reason);

    // 보상 포인트 적립
    void addRewardPoint(Long userId, int rewardPoints, String description);
}
