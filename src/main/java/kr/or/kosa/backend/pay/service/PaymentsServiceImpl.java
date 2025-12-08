package kr.or.kosa.backend.pay.service;

import kr.or.kosa.backend.pay.dto.TossConfirmResult;
import kr.or.kosa.backend.pay.dto.UpgradeQuoteResponse;
import kr.or.kosa.backend.pay.entity.Payments;
import kr.or.kosa.backend.pay.entity.Subscription;
import kr.or.kosa.backend.pay.repository.PaymentsMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentsServiceImpl implements PaymentsService {

    private final PaymentsMapper paymentsMapper;
    private final PointService pointService;
    private final TossPaymentsClient tossPaymentsClient;
    private final SubscriptionDomainService subscriptionDomainService;

    public PaymentsServiceImpl(PaymentsMapper paymentsMapper,
                               PointService pointService,
                               TossPaymentsClient tossPaymentsClient,
                               SubscriptionDomainService subscriptionDomainService) {
        this.paymentsMapper = paymentsMapper;
        this.pointService = pointService;
        this.tossPaymentsClient = tossPaymentsClient;
        this.subscriptionDomainService = subscriptionDomainService;
    }

    /**
     * READY 저장 + (포인트 전액 결제라면) 즉시 DONE 처리
     */
    @Override
    @Transactional
    public Payments savePayment(Payments payments) {

        // 0) 유저/주문 기본 검증 + orderId 세팅
        Long userId = validateUserAndInitOrderId(payments);

        // 최근 환불 이력에 따른 차단
        if (isUserInRefundBan(userId)) {
            throw new IllegalArgumentException(
                    "최근 2회 연속 환불로 인해 1개월 동안 결제가 제한된 계정입니다.");
        }

        // 1) 금액/포인트 정규화 + 유효성 검사 + 포인트 잔액 검증
        boolean pointOnly = normalizeAmountsAndValidate(payments, userId);

        // 2) DB 저장 (idempotent)
        Payments persisted = upsertPayment(payments);

        // 포인트 전액 결제라면 추가 처리
        if (pointOnly) {
            handlePointOnlyFlow(userId, persisted);
        }

        return persisted;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payments> getPaymentByOrderId(String orderId) {
        return paymentsMapper.findPaymentByOrderId(orderId);
    }

    @Override
    @Transactional
    public List<Subscription> getActiveSubscriptions(Long userId) {
        return subscriptionDomainService.getActiveSubscriptions(userId);
    }

    @Override
    @Transactional
    public Payments confirmAndSavePayment(Long userId, String paymentKey, String orderId, Long amount) {

        Payments existingPayment = paymentsMapper.findPaymentByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문 정보를 찾을 수 없습니다."));

        if (existingPayment.getUserId() == null || !existingPayment.getUserId().equals(userId)) {
            throw new IllegalStateException("본인 결제가 아니어서 승인할 수 없습니다.");
        }

        // 이미 DONE이면 그대로 반환 (멱등성)
        if ("DONE".equals(existingPayment.getStatus())) {
            return existingPayment;
        }

        if ("PROCESSING".equals(existingPayment.getStatus())) {
            throw new IllegalStateException("해당 주문은 승인 요청 처리 중입니다. 잠시 후 다시 시도해주세요.");
        }

        if (!"READY".equals(existingPayment.getStatus())) {
            throw new IllegalStateException(
                    "결제 상태가 승인 가능한 상태가 아닙니다. 현재 상태: " + existingPayment.getStatus());
        }

        // BigDecimal ↔ Long 비교
        BigDecimal storedAmount = existingPayment.getAmount();
        if (storedAmount == null) {
            throw new IllegalStateException("서버에 저장된 결제 금액이 없습니다.");
        }
        if (storedAmount.compareTo(BigDecimal.valueOf(amount)) != 0) {
            throw new IllegalStateException("요청 금액이 서버에 저장된 금액과 일치하지 않습니다.");
        }

        // 승인 처리 중 상태 전환 (READY → PROCESSING)
        int locked = paymentsMapper.updatePaymentStatusIfMatch(orderId, "READY", "PROCESSING");
        if (locked == 0) {
            Payments current = paymentsMapper.findPaymentByOrderId(orderId)
                    .orElseThrow(() -> new IllegalStateException("주문 정보가 즉시 조회되지 않습니다."));
            String currentStatus = current.getStatus();
            if ("DONE".equalsIgnoreCase(currentStatus)) {
                return current;
            }
            if ("PROCESSING".equalsIgnoreCase(currentStatus)) {
                throw new IllegalStateException("결제 승인 중입니다. 잠시 후 다시 시도해주세요.");
            }
            throw new IllegalStateException("결제 승인 진행 중이거나 상태가 변경되었습니다. 잠시 후 다시 시도해주세요.");
        }

        TossConfirmResult tossResult;
        try {
            tossResult = tossPaymentsClient.confirmPayment(paymentKey, orderId, amount);
        } catch (Exception e) {
            // 실패 시 PROCESSING → READY 롤백
            paymentsMapper.updatePaymentStatusIfMatch(orderId, "PROCESSING", "READY");
            throw e;
        }

        Payments confirmedPayment = Payments.builder()
                .paymentKey(paymentKey)
                .orderId(orderId)
                .status("DONE")
                .payMethod(tossResult.getPayMethod())
                .pgRawResponse(tossResult.getRawJson())
                .cardCompany(tossResult.getCardCompany())
                .cardApprovalNo(tossResult.getApproveNo())
                .approvedAt(tossResult.getApprovedAt())
                .build();

        paymentsMapper.updatePaymentStatus(confirmedPayment);

        // 포인트 실제 차감
        BigDecimal usedPoint = nvl(existingPayment.getUsedPoint());
        if (userId != null && usedPoint.compareTo(BigDecimal.ZERO) > 0) {
            pointService.usePoint(userId, usedPoint, orderId);
        }

        // 구독권 활성화
        subscriptionDomainService.grantSubscriptionToUser(orderId);

        return paymentsMapper.findPaymentByOrderId(orderId).orElseThrow(() ->
                new IllegalStateException("승인되었으나 DB에서 최종 조회 실패"));
    }

    @Override
    @Transactional(readOnly = true)
    public UpgradeQuoteResponse getUpgradeQuote(Long userId, String targetPlanCode) {
        return subscriptionDomainService.getUpgradeQuote(userId, targetPlanCode);
    }

    @Override
    @Transactional
    public Payments cancelPayment(Long userId, String paymentKey, String cancelReason) {

        Payments paymentToCancel = paymentsMapper.findPaymentByPaymentKey(paymentKey)
                .orElseThrow(() ->
                        new IllegalArgumentException("취소할 결제 정보를 찾을 수 없습니다."));

        if (paymentToCancel.getUserId() == null || !paymentToCancel.getUserId().equals(userId)) {
            throw new IllegalStateException("본인 결제가 아니어서 취소할 수 없습니다.");
        }

        if ("CANCELED".equals(paymentToCancel.getStatus())) {
            throw new IllegalStateException("이미 취소된 결제입니다.");
        }

        if (!"DONE".equals(paymentToCancel.getStatus())) {
            throw new IllegalStateException(
                    "결제 완료 상태에서만 환불할 수 있습니다. (현재 상태: " + paymentToCancel.getStatus() + ")");
        }

        // 승인 후 7일 이내만 환불 (READY 시점도 백업)
        LocalDateTime approvalTime = paymentToCancel.getApprovedAt();
        if (approvalTime == null) {
            approvalTime = paymentToCancel.getRequestedAt();
        }
        if (approvalTime == null) {
            throw new IllegalStateException("승인 시각이 없어 환불할 수 없습니다.");
        }
        if (approvalTime.isBefore(LocalDateTime.now().minusDays(7))) {
            throw new IllegalArgumentException("승인 후 7일이 지나 환불할 수 없습니다.");
        }

        // 토스 취소 API 호출
        String newStatus = tossPaymentsClient.cancelPayment(paymentKey, cancelReason);

        paymentsMapper.updatePaymentStatusToCanceled(paymentToCancel.getOrderId(), newStatus);
        subscriptionDomainService.cancelSubscriptionByOrderId(paymentToCancel.getOrderId());

        BigDecimal usedPoint = nvl(paymentToCancel.getUsedPoint());
        if (usedPoint.compareTo(BigDecimal.ZERO) > 0) {
            pointService.refundPoint(userId, usedPoint, paymentToCancel.getOrderId(), cancelReason);
        }

        return paymentsMapper.findPaymentByOrderId(paymentToCancel.getOrderId())
                .orElse(paymentToCancel);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Payments> getPaymentHistory(Long userId, LocalDate from, LocalDate to, String status) {
        LocalDateTime fromDt = (from != null) ? from.atStartOfDay() : null;
        LocalDateTime toDt = (to != null) ? to.plusDays(1).atStartOfDay() : null;
        String normalizedStatus = (status == null || status.isBlank()) ? null : status.toUpperCase();
        return paymentsMapper.findPaymentsInRange(userId, fromDt, toDt, normalizedStatus);
    }

    // ================== 역할별 private 메소드 ==================

    private Long validateUserAndInitOrderId(Payments payments) {
        Long userId = payments.getUserId();
        if (userId == null) {
            throw new IllegalArgumentException("필수 파라미터 userId가 없습니다.");
        }

        // orderId 없으면 생성
        if (payments.getOrderId() == null || payments.getOrderId().isBlank()) {
            String newOrderId = "ORD-" + System.currentTimeMillis()
                    + "-" + UUID.randomUUID().toString().substring(0, 8);
            payments.setOrderId(newOrderId);
        }
        return userId;
    }

    /**
     * 금액/포인트 정규화 + 유효성 검사 + 포인트 잔액 검증
     */
    private boolean normalizeAmountsAndValidate(Payments payments, Long userId) {

        BigDecimal clientAmount   = nvl(payments.getAmount());          // 클라이언트 전달 최종금액
        BigDecimal originalAmount = nvl(payments.getOriginalAmount());  // 플랜 정가/추가금
        BigDecimal usedPoint      = nvl(payments.getUsedPoint());       // 사용 포인트

        BigDecimal serverPrice =
                subscriptionDomainService.getMonthlyPrice(payments.getPlanCode());

        BigDecimal upgradeExtra = getUpgradeExtraAmountIfApplicable(userId, payments.getPlanCode());
        if (upgradeExtra != null && upgradeExtra.compareTo(BigDecimal.ZERO) > 0) {
            serverPrice = upgradeExtra;
        }

        originalAmount = applyServerPriceIfNecessary(
                payments, originalAmount, clientAmount, usedPoint, serverPrice
        );

        // 사용 포인트가 정가보다 크면 정가까지만 사용하도록 보정
        if (usedPoint.compareTo(originalAmount) > 0) {
            usedPoint = originalAmount;
        }

        validateAmountRanges(originalAmount, usedPoint);

        BigDecimal expectedAmount = originalAmount.subtract(usedPoint);
        if (expectedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("결제 금액이 0보다 작을 수 없습니다.");
        }

        boolean pointOnly = expectedAmount.compareTo(BigDecimal.ZERO) == 0;
        if (pointOnly && usedPoint.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("포인트 전액 결제 시 사용 포인트는 0보다 커야 합니다.");
        }

        if (!pointOnly) {
            if (clientAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("결제 금액이 올바르지 않습니다.");
            }
            if (clientAmount.compareTo(expectedAmount) != 0) {
                throw new IllegalArgumentException("요청 금액이 서버 금액과 일치하지 않습니다.");
            }
        }

        payments.setAmount(expectedAmount);
        payments.setOriginalAmount(originalAmount);
        payments.setUsedPoint(usedPoint);

        if (usedPoint.compareTo(BigDecimal.ZERO) > 0) {
            pointService.validatePointBalance(userId, usedPoint);
        }

        payments.setStatus(pointOnly ? "DONE" : "READY");
        payments.setRequestedAt(LocalDateTime.now());

        return pointOnly;
    }

    private BigDecimal getUpgradeExtraAmountIfApplicable(Long userId, String planCode) {
        if (planCode == null || userId == null) {
            return null;
        }
        if (!"PRO".equalsIgnoreCase(planCode)) {
            return null;
        }

        try {
            UpgradeQuoteResponse quote = subscriptionDomainService.getUpgradeQuote(userId, planCode);
            if (quote != null
                    && quote.isUpgrade()
                    && quote.getExtraAmount() != null
                    && quote.getExtraAmount().compareTo(BigDecimal.ZERO) > 0) {
                return quote.getExtraAmount();
            }
        } catch (Exception e) {
            System.err.println("upgrade quote 조회 실패: " + e.getMessage());
        }

        return null;
    }

    private BigDecimal applyServerPriceIfNecessary(Payments payments,
                                                   BigDecimal originalAmount,
                                                   BigDecimal clientAmount,
                                                   BigDecimal usedPoint,
                                                   BigDecimal serverPrice) {

        if (serverPrice.compareTo(BigDecimal.ZERO) > 0) {
            // 서버 정가가 있는 플랜
            payments.setOriginalAmount(serverPrice);
            return serverPrice;
        }

        // 서버 정가가 없는 특수 케이스 + 포인트 미사용 → 클라 amount 를 원가로 사용
        if (serverPrice.compareTo(BigDecimal.ZERO) <= 0
                && originalAmount.compareTo(BigDecimal.ZERO) <= 0
                && usedPoint.compareTo(BigDecimal.ZERO) == 0) {

            if (clientAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("결제 금액이 올바르지 않습니다.");
            }
            payments.setOriginalAmount(clientAmount);
            return clientAmount;
        }

        return originalAmount;
    }

    private void validateAmountRanges(BigDecimal originalAmount, BigDecimal usedPoint) {
        if (originalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("원래 결제 금액이 올바르지 않습니다.");
        }
        if (usedPoint.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("사용 포인트는 0보다 작을 수 없습니다.");
        }
        if (usedPoint.compareTo(originalAmount) > 0) {
            throw new IllegalArgumentException("사용 포인트가 결제 금액보다 클 수 없습니다.");
        }
    }

    /**
     * 결제 row upsert (INSERT or UPDATE), DONE 상태면 기존 row 재사용
     */
    private Payments upsertPayment(Payments payments) {
        Optional<Payments> existingOpt =
                paymentsMapper.findPaymentByOrderId(payments.getOrderId());

        if (existingOpt.isEmpty()) {
            paymentsMapper.insertPayment(payments);
            return payments;
        }

        Payments existing = existingOpt.get();

        // 이미 DONE이면 다시 처리 X (포인트/구독 중복 방지)
        if ("DONE".equalsIgnoreCase(existing.getStatus())) {
            return existing;
        }

        existing.setUserId(payments.getUserId());
        existing.setPlanCode(payments.getPlanCode());
        existing.setOrderName(payments.getOrderName());
        existing.setCustomerName(payments.getCustomerName());
        existing.setOriginalAmount(payments.getOriginalAmount());
        existing.setUsedPoint(payments.getUsedPoint());
        existing.setAmount(payments.getAmount());
        existing.setStatus(payments.getStatus());
        existing.setRequestedAt(payments.getRequestedAt());

        // 이름은 READY지만 status 필드에 따라 DONE 도 저장 가능
        paymentsMapper.updatePaymentForReady(existing);
        return existing;
    }

    /**
     * 포인트 전액 결제 플로우 처리
     */
    private void handlePointOnlyFlow(Long userId, Payments persisted) {

        BigDecimal usedPoint = nvl(persisted.getUsedPoint());

        // 포인트 차감
        if (usedPoint.compareTo(BigDecimal.ZERO) > 0) {
            pointService.usePoint(userId, usedPoint, persisted.getOrderId());
        }

        // 결제수단/승인 정보 간단 기록
        Payments doneUpdate = Payments.builder()
                .orderId(persisted.getOrderId())
                .paymentKey(null)
                .status("DONE")
                .payMethod("POINT_ONLY")
                .approvedAt(LocalDateTime.now())
                .pgRawResponse("POINT_ONLY")
                .build();

        paymentsMapper.updatePaymentStatus(doneUpdate);

        // 구독권 부여
        subscriptionDomainService.grantSubscriptionToUser(persisted.getOrderId());

        persisted.setStatus("DONE");
    }

    // ================== 공통 유틸 ==================

    private boolean isUserInRefundBan(Long userId) {
        List<Payments> recent = paymentsMapper.findRecentPaymentsByUser(userId, 2);
        if (recent == null || recent.size() < 2) {
            return false;
        }

        Payments latest   = recent.get(0);
        Payments previous = recent.get(1);

        if (!"CANCELED".equals(latest.getStatus())
                || !"CANCELED".equals(previous.getStatus())) {
            return false;
        }

        LocalDateTime latestRequestedAt = latest.getRequestedAt();
        if (latestRequestedAt == null) {
            return false;
        }

        return latestRequestedAt.isAfter(LocalDateTime.now().minusDays(30));
    }

    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
