package kr.or.kosa.backend.pay.service;

import kr.or.kosa.backend.pay.entity.PointHistory;
import kr.or.kosa.backend.pay.entity.UserPoint;
import kr.or.kosa.backend.pay.repository.PointMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional
public class PointServiceImpl implements PointService {

    private final PointMapper pointMapper;

    @Override
    public void usePoint(Long userId, BigDecimal usePoint, String orderId) {

        if (usePoint == null || usePoint.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        UserPoint userPoint = pointMapper.findUserPointByUserId(userId)
                .orElseGet(() -> {
                    UserPoint up = UserPoint.builder()
                            .userId(userId)
                            .balance(BigDecimal.ZERO)
                            .build();
                    pointMapper.insertUserPoint(up);
                    return up;
                });

        int updated = pointMapper.usePoint(userId, usePoint);
        if (updated != 1) {
            throw new IllegalStateException("포인트가 부족하여 결제를 진행할 수 없습니다.");
        }

        PointHistory history = PointHistory.builder()
                .userId(userId)
                .changeAmount(usePoint.negate()) // -값
                .type("USE")
                .paymentOrderId(orderId)
                .description("구독 결제 시 포인트 사용")
                .build();

        pointMapper.insertPointHistory(history);
    }

    @Override
    public void refundPoint(Long userId, BigDecimal usedPoint, String orderId, String reason) {

        if (usedPoint == null || usedPoint.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        int updated = pointMapper.refundPoint(userId, usedPoint);
        if (updated != 1) {
            throw new IllegalStateException("포인트 환불 처리 중 오류가 발생했습니다.");
        }

        PointHistory history = PointHistory.builder()
                .userId(userId)
                .changeAmount(usedPoint) // +값
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

    @Override
    public void validatePointBalance(Long userId, BigDecimal usePoint) {
        if (usePoint == null || usePoint.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        UserPoint userPoint = pointMapper.findUserPointByUserId(userId).orElse(null);
        BigDecimal balance = (userPoint != null && userPoint.getBalance() != null)
                ? userPoint.getBalance()
                : BigDecimal.ZERO;

        if (balance.compareTo(usePoint) < 0) {
            throw new IllegalStateException(
                    "보유 포인트가 부족합니다. (보유: " + balance + "P, 사용 요청: " + usePoint + "P)"
            );
        }
    }

    @Override
    public void addRewardPoint(Long userId, int rewardPoints, String description) {
        if (rewardPoints <= 0) {
            return;
        }
        BigDecimal reward = BigDecimal.valueOf(rewardPoints);

        // 유저 포인트 row가 없으면 생성
        pointMapper.findUserPointByUserId(userId)
                .orElseGet(() -> {
                    UserPoint up = UserPoint.builder()
                            .userId(userId)
                            .balance(BigDecimal.ZERO)
                            .build();
                    pointMapper.insertUserPoint(up);
                    return up;
                });

        // 포인트 적립
        pointMapper.addRewardPoint(userId, reward);

        // 이력 기록
        PointHistory history = PointHistory.builder()
                .userId(userId)
                .changeAmount(reward)
                .type("REWARD")
                .description(description)
                .build();

        pointMapper.insertPointHistory(history);
    }
}
