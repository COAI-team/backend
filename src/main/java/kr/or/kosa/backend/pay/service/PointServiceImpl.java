package kr.or.kosa.backend.pay.service;

import kr.or.kosa.backend.pay.entity.PointHistory;
import kr.or.kosa.backend.pay.entity.UserPoint;
import kr.or.kosa.backend.pay.repository.PointMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PointServiceImpl implements PointService {

    private final PointMapper pointMapper;

    @Override
    public void usePoint(String userId, int usePoint, String orderId) {

        if (usePoint <= 0) {
            return; // 사용할 포인트가 없으면 스킵
        }

        // user_points row가 없으면 0원에서 시작하게 생성 (방어코드)
        UserPoint userPoint = pointMapper.findUserPointByUserId(userId)
                .orElseGet(() -> {
                    UserPoint up = UserPoint.builder()
                            .userId(userId)
                            .balance(0)
                            .build();
                    pointMapper.insertUserPoint(up);
                    return up;
                });

        // 실제 차감 쿼리 (balance >= usePoint 조건 포함)
        int updated = pointMapper.usePoint(userId, usePoint);
        if (updated != 1) {
            throw new IllegalStateException("포인트가 부족하여 결제를 진행할 수 없습니다.");
        }

        // 히스토리 기록
        PointHistory history = PointHistory.builder()
                .userId(userId)
                .changeAmount(-usePoint)
                .type("USE")
                .paymentOrderId(orderId)
                .description("구독 결제 시 포인트 사용")
                .build();

        pointMapper.insertPointHistory(history);
    }

    @Override
    public void refundPoint(String userId, int usedPoint, String orderId, String reason) {

        if (usedPoint <= 0) {
            return; // 환불할 포인트 없음
        }

        int updated = pointMapper.refundPoint(userId, usedPoint);
        if (updated != 1) {
            throw new IllegalStateException("포인트 환불 처리 중 오류가 발생했습니다.");
        }

        PointHistory history = PointHistory.builder()
                .userId(userId)
                .changeAmount(usedPoint)
                .type("REFUND")
                .paymentOrderId(orderId)
                .description(
                        (reason != null && !reason.isBlank())
                                ? "결제 환불: " + reason
                                : "구독 결제 환불로 인한 포인트 복구"
                )
                .build();

        pointMapper.insertPointHistory(history);
    }

    /**
     * READY 단계에서 포인트 잔액 충분한지 확인만 하는 메소드.
     * DB balance는 변경하지 않음.
     */
    @Override
    public void validatePointBalance(String userId, int usePoint) {
        if (usePoint <= 0) {
            return;
        }

        UserPoint userPoint = pointMapper.findUserPointByUserId(userId).orElse(null);
        int balance = (userPoint != null) ? userPoint.getBalance() : 0;

        if (balance < usePoint) {
            throw new IllegalStateException(
                    "보유 포인트가 부족합니다. (보유: " + balance + "P, 사용 요청: " + usePoint + "P)"
            );
        }
    }
}
