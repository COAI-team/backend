package kr.or.kosa.backend.pay.service;

import kr.or.kosa.backend.pay.dto.UpgradeQuoteResponse;
import kr.or.kosa.backend.pay.entity.Payments;
import kr.or.kosa.backend.pay.entity.Subscription;
import kr.or.kosa.backend.pay.entity.SubscriptionPlan;
import kr.or.kosa.backend.pay.repository.PaymentsMapper;
import kr.or.kosa.backend.pay.repository.SubscriptionMapper;
import kr.or.kosa.backend.pay.repository.SubscriptionPlanMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class SubscriptionDomainService {

    private static final long SUBSCRIPTION_DAYS = 30L;

    private final SubscriptionMapper subscriptionMapper;
    private final SubscriptionPlanMapper subscriptionPlanMapper;
    private final PaymentsMapper paymentsMapper;

    public SubscriptionDomainService(SubscriptionMapper subscriptionMapper,
                                     SubscriptionPlanMapper subscriptionPlanMapper,
                                     PaymentsMapper paymentsMapper) {
        this.subscriptionMapper = subscriptionMapper;
        this.subscriptionPlanMapper = subscriptionPlanMapper;
        this.paymentsMapper = paymentsMapper;
    }

    /**
     * 구독 만료 처리 + ACTIVE 목록 조회
     */
    public List<Subscription> getActiveSubscriptions(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        subscriptionMapper.expireSubscriptionsByUserId(userId);
        return subscriptionMapper.findActiveSubscriptionsByUserId(userId);
    }

    /**
     * 결제(orderId) 기준으로 구독권 부여 / 업그레이드 처리
     */
    public void grantSubscriptionToUser(String orderId) {
        Payments payment = paymentsMapper.findPaymentByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));

        String userId = payment.getUserId();
        if (userId == null || userId.isEmpty()) {
            userId = payment.getCustomerName();
        }

        String planCode = payment.getPlanCode();
        if (planCode == null || planCode.isEmpty()) {
            planCode = payment.getOrderName();
        }

        LocalDateTime now = LocalDateTime.now();

        // BASIC → PRO 업그레이드 (남은 기간 승계 로직)
        if (userId != null
                && !userId.isEmpty()
                && "PRO".equalsIgnoreCase(planCode)) {

            Optional<Subscription> basicOpt =
                    subscriptionMapper.findLatestActiveSubscriptionByUserIdAndType(userId, "BASIC");

            if (basicOpt.isPresent()) {
                Subscription basicSub = basicOpt.get();
                LocalDateTime basicEnd = basicSub.getEndDate();

                if (basicEnd != null && basicEnd.isAfter(now)) {

                    // 기존 BASIC 구독 종료 처리
                    subscriptionMapper.updateSubscriptionStatusToCanceled(
                            basicSub.getOrderId(),
                            "CANCELED"
                    );

                    // PRO 구독 생성 (남은 기간 그대로)
                    Subscription proSubscription = Subscription.builder()
                            .userId(userId)
                            .orderId(orderId)
                            .subscriptionType("PRO")
                            .startDate(now)
                            .endDate(basicEnd)
                            .status("ACTIVE")
                            .build();

                    int inserted = subscriptionMapper.insertSubscription(proSubscription);
                    if (inserted != 1) {
                        throw new RuntimeException("구독권 업그레이드 정보 DB 저장 실패");
                    }

                    return;
                }
            }
        }

        // 일반 구독 1개월
        LocalDateTime endDate = now.plusMonths(1);

        Subscription newSubscription = Subscription.builder()
                .userId(userId)
                .orderId(orderId)
                .subscriptionType(planCode)
                .startDate(now)
                .endDate(endDate)
                .status("ACTIVE")
                .build();

        int result = subscriptionMapper.insertSubscription(newSubscription);
        if (result != 1) {
            throw new RuntimeException("구독권 정보 DB 저장 실패");
        }
    }

    /**
     * 주문기반 구독 취소 (환불시 호출)
     */
    public void cancelSubscriptionByOrderId(String orderId) {
        subscriptionMapper.updateSubscriptionStatusToCanceled(orderId, "CANCELED");
    }

    /**
     * BASIC → PRO 업그레이드 견적 계산 (기존 로직 그대로 이동)
     */
    public UpgradeQuoteResponse getUpgradeQuote(String userId, String targetPlanCode) {

        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId는 필수입니다.");
        }
        if (targetPlanCode == null || targetPlanCode.isBlank()) {
            throw new IllegalArgumentException("planCode는 필수입니다.");
        }

        String normalizedTarget = targetPlanCode.toUpperCase();

        // BASIC → PRO가 아닌 경우: 업그레이드 아님
        if (!"PRO".equals(normalizedTarget)) {
            return UpgradeQuoteResponse.builder()
                    .upgrade(false)
                    .fromPlan(null)
                    .toPlan(normalizedTarget)
                    .usedDays(0)
                    .remainingDays(0)
                    .extraAmount(BigDecimal.ZERO)
                    .basicEndDate(null)
                    .build();
        }

        // 최신 ACTIVE BASIC 구독 1개 조회
        return subscriptionMapper.findLatestActiveSubscriptionByUserIdAndType(userId, "BASIC")
                .map(basicSub -> {

                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime start = basicSub.getStartDate();
                    LocalDateTime end = basicSub.getEndDate();

                    // 이미 끝난 BASIC이면 업그레이드 없음
                    if (end == null || !end.isAfter(now)) {
                        return UpgradeQuoteResponse.builder()
                                .upgrade(false)
                                .fromPlan("BASIC")
                                .toPlan("PRO")
                                .usedDays(0)
                                .remainingDays(0)
                                .extraAmount(BigDecimal.ZERO)
                                .basicEndDate(null)
                                .build();
                    }

                    long totalDays = 0;
                    if (start != null && end != null) {
                        totalDays = ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate());
                    }
                    if (totalDays <= 0) {
                        totalDays = SUBSCRIPTION_DAYS;
                    }

                    long usedDays = 0;
                    if (start != null) {
                        usedDays = ChronoUnit.DAYS.between(start.toLocalDate(), now.toLocalDate());
                        if (usedDays < 0) usedDays = 0;
                        if (usedDays > totalDays) usedDays = totalDays;
                    }

                    long remainingDays = totalDays - usedDays;
                    if (remainingDays <= 0) {
                        return UpgradeQuoteResponse.builder()
                                .upgrade(false)
                                .fromPlan("BASIC")
                                .toPlan("PRO")
                                .usedDays(usedDays)
                                .remainingDays(0)
                                .extraAmount(BigDecimal.ZERO)
                                .basicEndDate(null)
                                .build();
                    }

                    BigDecimal basicPrice = getMonthlyPrice("BASIC");
                    BigDecimal proPrice   = getMonthlyPrice("PRO");
                    BigDecimal diff       = proPrice.subtract(basicPrice);

                    if (diff.compareTo(BigDecimal.ZERO) <= 0) {
                        return UpgradeQuoteResponse.builder()
                                .upgrade(false)
                                .fromPlan("BASIC")
                                .toPlan("PRO")
                                .usedDays(usedDays)
                                .remainingDays(remainingDays)
                                .extraAmount(BigDecimal.ZERO)
                                .basicEndDate(null)
                                .build();
                    }

                    BigDecimal ratio = BigDecimal.valueOf(remainingDays)
                            .divide(BigDecimal.valueOf(totalDays), 6, RoundingMode.HALF_UP);

                    BigDecimal extraAmount = diff
                            .multiply(ratio)
                            .setScale(0, RoundingMode.CEILING);   // 원단위 올림

                    String endStr = end.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                    return UpgradeQuoteResponse.builder()
                            .upgrade(true)
                            .fromPlan("BASIC")
                            .toPlan("PRO")
                            .usedDays(usedDays)
                            .remainingDays(remainingDays)
                            .extraAmount(extraAmount)
                            .basicEndDate(endStr)
                            .build();
                })
                .orElseGet(() ->
                        UpgradeQuoteResponse.builder()
                                .upgrade(false)
                                .fromPlan(null)
                                .toPlan("PRO")
                                .usedDays(0)
                                .remainingDays(0)
                                .extraAmount(BigDecimal.ZERO)
                                .basicEndDate(null)
                                .build()
                );
    }

    /**
     * 플랜 코드 → 월 요금
     * FREE 는 0, DB에 없거나 비활성화면 0 리턴 (기존 정책 유지)
     */
    public BigDecimal getMonthlyPrice(String planCode) {
        if (planCode == null || planCode.isBlank()) {
            return BigDecimal.ZERO;
        }

        String code = planCode.toUpperCase();

        // FREE 플랜은 실제 결제 없음
        if ("FREE".equals(code)) {
            return BigDecimal.ZERO;
        }

        SubscriptionPlan plan =
                subscriptionPlanMapper.findActiveByPlanCode(code);

        if (plan == null) {
            return BigDecimal.ZERO;
        }

        return plan.getMonthlyFee();
    }
}
