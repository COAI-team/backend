package kr.or.kosa.backend.pay.service;

public interface PointService {

    /**
     * 결제 확정 시 포인트 사용하는 로직.
     * - usePoint <= 0 이면 아무 것도 안 함
     * - 잔액 부족이면 IllegalStateException 던짐
     */
    void usePoint(String userId, int usePoint, String orderId);

    /**
     * 결제 취소 시 포인트 환불 로직.
     * - usedPoint <= 0 이면 아무 것도 안 함
     */
    void refundPoint(String userId, int usedPoint, String orderId, String reason);

    /**
     * 결제 READY 단계에서, 포인트를 사용할 수 있는지(잔액 충분한지) 사전 검증만 하는 메소드.
     * - 실제로 balance를 변경하지 않는다.
     * - 잔액 부족이면 IllegalStateException 던짐.
     */
    void validatePointBalance(String userId, int usePoint);
}