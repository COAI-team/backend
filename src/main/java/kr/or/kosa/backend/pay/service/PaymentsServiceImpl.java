package kr.or.kosa.backend.pay.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.pay.dto.UpgradeQuoteResponse;
import kr.or.kosa.backend.pay.entity.Payments;
import kr.or.kosa.backend.pay.entity.Subscription;
import kr.or.kosa.backend.pay.entity.SubscriptionPlan;
import kr.or.kosa.backend.pay.repository.PaymentsMapper;
import kr.or.kosa.backend.pay.repository.SubscriptionMapper;
import kr.or.kosa.backend.pay.repository.SubscriptionPlanMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional
public class PaymentsServiceImpl implements PaymentsService {

    private final PaymentsMapper paymentsMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final RestTemplate restTemplate;

    private final PointService pointService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final SubscriptionPlanMapper subscriptionPlanMapper;

    @Value("${toss.payments.key}")
    private String secretKey;

    public PaymentsServiceImpl(PaymentsMapper paymentsMapper,
                               SubscriptionMapper subscriptionMapper,
                               PointService pointService,
                               SubscriptionPlanMapper subscriptionPlanMapper) {
        this.paymentsMapper = paymentsMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.pointService = pointService;
        this.subscriptionPlanMapper = subscriptionPlanMapper;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public Payments savePayment(Payments payments) {

        // 0-0) userId 필수
        String userId = payments.getUserId();
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("결제를 진행하려면 userId가 필요합니다.");
        }

        // orderId 없으면 서버에서 생성
        if (payments.getOrderId() == null || payments.getOrderId().isBlank()) {
            String newOrderId = "ORD-" + System.currentTimeMillis()
                    + "-" + UUID.randomUUID().toString().substring(0, 8);
            payments.setOrderId(newOrderId);
        }

        // 최근 2회 연속 환불 + 30일 이내 → 결제 차단
        if (isUserInRefundBan(userId)) {
            throw new IllegalArgumentException(
                    "최근 2회 연속 환불로 인해 1개월 동안 결제가 제한된 계정입니다.");
        }

        // 기본 상태값
        payments.setStatus("READY");
        payments.setRequestedAt(LocalDateTime.now());

        // 금액/포인트 BigDecimal 정규화
        BigDecimal clientAmount   = nvl(payments.getAmount());          // 프론트에서 넘어온 최종 결제 금액
        BigDecimal originalAmount = nvl(payments.getOriginalAmount());  // 플랜 원가
        BigDecimal usedPoint      = nvl(payments.getUsedPoint());       // 사용 포인트

        // 0-3) 플랜 코드 기반 서버 정가 적용
        String planCode = payments.getPlanCode();
        BigDecimal serverPrice = getMonthlyPrice(planCode); // subscription_plans.monthly_fee

        // BASIC / PRO 처럼 서버에 정가가 정의된 플랜이면 무조건 서버 금액으로 덮어쓴다
        if (serverPrice.compareTo(BigDecimal.ZERO) > 0) {
            originalAmount = serverPrice;
            payments.setOriginalAmount(serverPrice);
        }

        // 1) 서버 정가 없는 플랜 + 포인트 미사용 → 클라 amount를 원가로 사용
        if (serverPrice.compareTo(BigDecimal.ZERO) <= 0
                && originalAmount.compareTo(BigDecimal.ZERO) <= 0
                && usedPoint.compareTo(BigDecimal.ZERO) == 0) {

            if (clientAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("결제 금액이 올바르지 않습니다.");
            }
            originalAmount = clientAmount;
            payments.setOriginalAmount(originalAmount);
        }

        // 2) 범위 검증
        if (originalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("원래 결제 금액이 올바르지 않습니다.");
        }
        if (usedPoint.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("사용 포인트는 0보다 작을 수 없습니다.");
        }
        if (usedPoint.compareTo(originalAmount) > 0) {
            throw new IllegalArgumentException("사용 포인트가 결제 금액보다 클 수 없습니다.");
        }

        BigDecimal expectedAmount = originalAmount.subtract(usedPoint);
        if (expectedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("최종 결제 금액이 0 이하가 될 수 없습니다.");
        }

        // 3) 최종 결제 금액 검증 (클라 amount vs 서버 계산값)
        if (clientAmount.compareTo(BigDecimal.ZERO) != 0
                && clientAmount.compareTo(expectedAmount) != 0) {
            throw new IllegalArgumentException("요청된 결제 금액과 포인트 적용 금액이 일치하지 않습니다.");
        }

        // 서버 계산값으로 강제 세팅
        payments.setAmount(expectedAmount);

        // 4) 포인트 잔액 사전 검증
        if (usedPoint.compareTo(BigDecimal.ZERO) > 0) {
            pointService.validatePointBalance(userId, usedPoint);
        }

        // 5) DB 저장 (idempotent)
        Optional<Payments> existingOpt = paymentsMapper.findPaymentByOrderId(payments.getOrderId());

        if (existingOpt.isEmpty()) {
            paymentsMapper.insertPayment(payments);
            return payments;
        }

        Payments existing = existingOpt.get();

        if ("DONE".equalsIgnoreCase(existing.getStatus())) {
            throw new IllegalStateException("이미 결제가 완료된 주문입니다. 새로운 결제는 다른 orderId를 사용해야 합니다.");
        }

        existing.setUserId(payments.getUserId());
        existing.setPlanCode(payments.getPlanCode());
        existing.setOrderName(payments.getOrderName());
        existing.setCustomerName(payments.getCustomerName());
        existing.setOriginalAmount(payments.getOriginalAmount());
        existing.setUsedPoint(payments.getUsedPoint());
        existing.setAmount(payments.getAmount());
        existing.setStatus(payments.getStatus());       // READY
        existing.setRequestedAt(payments.getRequestedAt());

        paymentsMapper.updatePaymentForReady(existing);

        return existing;
    }

    @Override
    public Optional<Payments> getPaymentByOrderId(String orderId) {
        return paymentsMapper.findPaymentByOrderId(orderId);
    }

    @Override
    public List<Subscription> getActiveSubscriptions(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }

        subscriptionMapper.expireSubscriptionsByUserId(userId);
        return subscriptionMapper.findActiveSubscriptionsByUserId(userId);
    }

    @Override
    public Payments confirmAndSavePayment(String paymentKey, String orderId, Long amount) {

        Payments existingPayment = paymentsMapper.findPaymentByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문 정보를 찾을 수 없습니다."));

        if ("DONE".equals(existingPayment.getStatus())) {
            return existingPayment;
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

        // 토스 승인 API 호출
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(secretKey, "");

        Map<String, Object> body = new HashMap<>();
        body.put("paymentKey", paymentKey);
        body.put("orderId", orderId);
        body.put("amount", amount);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String url = "https://api.tosspayments.com/v1/payments/confirm";

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();

            if (response.getStatusCode() == HttpStatus.OK
                    && responseBody != null
                    && "DONE".equals(responseBody.get("status"))) {

                String tossMethod = (String) responseBody.get("method");
                String internalPayMethod = convertTossMethodToInternal(tossMethod, responseBody);

                String rawJson = toJsonString(responseBody);

                Map<String, Object> cardMap = null;
                Object cardObj = responseBody.get("card");
                if (cardObj instanceof Map<?, ?> m) {
                    cardMap = (Map<String, Object>) m;
                }

                String cardCompany = null;
                String approveNo   = null;
                LocalDateTime approvedAt = null;

                if (cardMap != null) {
                    cardCompany = (String) cardMap.get("issuerCode");
                    approveNo   = (String) cardMap.get("approveNo");
                }
                String approvedAtRaw = (String) responseBody.get("approvedAt");
                if (approvedAtRaw != null) {
                    approvedAt = OffsetDateTime.parse(approvedAtRaw)
                            .atZoneSameInstant(ZoneId.of("Asia/Seoul"))
                            .toLocalDateTime();
                }

                System.out.println("[TOSS CONFIRM SUCCESS] orderId=" + orderId
                        + ", paymentKey=" + paymentKey
                        + ", method=" + internalPayMethod
                        + ", amount=" + amount
                        + ", cardCompany=" + cardCompany
                        + ", approveNo=" + approveNo);

                Payments confirmedPayment = Payments.builder()
                        .paymentKey(paymentKey)
                        .orderId(orderId)
                        .status("DONE")
                        .payMethod(internalPayMethod)
                        .pgRawResponse(rawJson)
                        .cardCompany(cardCompany)
                        .cardApprovalNo(approveNo)
                        .approvedAt(approvedAt)
                        .build();

                paymentsMapper.updatePaymentStatus(confirmedPayment);

                // 포인트 실제 차감
                String userId = existingPayment.getUserId();
                BigDecimal usedPoint = nvl(existingPayment.getUsedPoint());
                if (userId != null && !userId.isEmpty()
                        && usedPoint.compareTo(BigDecimal.ZERO) > 0) {
                    pointService.usePoint(userId, usedPoint, orderId);
                }

                // 구독권 활성화
                grantSubscriptionToUser(orderId);

                return this.getPaymentByOrderId(orderId).orElseThrow(() ->
                        new IllegalStateException("승인되었으나 DB에서 최종 조회 실패"));

            } else {
                String errorMessage = (responseBody != null)
                        ? String.valueOf(responseBody.get("message"))
                        : "unknown error";
                throw new IllegalStateException("토스페이먼츠 승인 거부: " + errorMessage);
            }

        } catch (HttpClientErrorException e) {
            System.err.println("[TOSS CONFIRM ERROR] status=" + e.getStatusCode()
                    + ", body=" + e.getResponseBodyAsString());
            throw new IllegalStateException("토스페이먼츠 승인 거부: " + e.getResponseBodyAsString(), e);

        } catch (Exception e) {
            if (e instanceof IllegalStateException || e instanceof IllegalArgumentException) {
                throw e;
            }
            throw new RuntimeException("결제 승인 중 알 수 없는 오류 발생: " + e.getMessage(), e);
        }
    }

    private void grantSubscriptionToUser(String orderId) {
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

        // BASIC → PRO 업그레이드
        if (userId != null
                && !userId.isEmpty()
                && "PRO".equalsIgnoreCase(planCode)) {

            Optional<Subscription> basicOpt =
                    subscriptionMapper.findLatestActiveSubscriptionByUserIdAndType(userId, "BASIC");

            if (basicOpt.isPresent()) {
                Subscription basicSub = basicOpt.get();

                LocalDateTime basicEnd = basicSub.getEndDate();

                if (basicEnd != null && basicEnd.isAfter(now)) {

                    subscriptionMapper.updateSubscriptionStatusToCanceled(
                            basicSub.getOrderId(),
                            "CANCELED"
                    );

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

    @Override
    @Transactional
    public Payments cancelPayment(String paymentKey, String cancelReason) {

        Payments paymentToCancel = paymentsMapper.findPaymentByPaymentKey(paymentKey)
                .orElseThrow(() ->
                        new IllegalArgumentException("취소할 결제 정보를 찾을 수 없습니다."));

        if ("CANCELED".equals(paymentToCancel.getStatus())) {
            throw new IllegalStateException("이미 취소된 결제입니다.");
        }

        if (!"DONE".equals(paymentToCancel.getStatus())) {
            throw new IllegalStateException(
                    "결제 완료 상태에서만 환불할 수 있습니다. (현재 상태: " + paymentToCancel.getStatus() + ")");
        }

        LocalDateTime requestedAt = paymentToCancel.getRequestedAt();
        if (requestedAt.isBefore(LocalDateTime.now().minusDays(7))) {
            throw new IllegalArgumentException("결제 후 7일이 지난 건은 환불할 수 없습니다.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(secretKey, "");

        Map<String, Object> body = new HashMap<>();
        body.put("cancelReason", cancelReason);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String url = String.format("https://api.tosspayments.com/v1/payments/%s/cancel", paymentKey);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                String newStatus = (String) response.getBody().get("status");

                paymentsMapper.updatePaymentStatusToCanceled(paymentToCancel.getOrderId(), newStatus);
                subscriptionMapper.updateSubscriptionStatusToCanceled(paymentToCancel.getOrderId(), "CANCELED");

                String userId = paymentToCancel.getUserId();
                BigDecimal usedPoint = nvl(paymentToCancel.getUsedPoint());
                if (userId != null && !userId.isEmpty()
                        && usedPoint.compareTo(BigDecimal.ZERO) > 0) {
                    pointService.refundPoint(userId, usedPoint, paymentToCancel.getOrderId(), cancelReason);
                }

                return paymentsMapper.findPaymentByOrderId(paymentToCancel.getOrderId())
                        .orElse(paymentToCancel);

            } else {
                throw new IllegalStateException("토스페이먼츠 환불 요청 실패: HTTP " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("토스페이먼츠 환불 거부: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RuntimeException("환불 처리 중 알 수 없는 오류 발생", e);
        }
    }

    private boolean isUserInRefundBan(String userId) {
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

    private String convertTossMethodToInternal(String tossMethod, Map<String, Object> responseBody) {
        if (tossMethod == null) return "UNKNOWN";

        switch (tossMethod) {
            case "카드":
            case "CARD":
                return "CARD";

            case "계좌이체":
            case "ACCOUNT_TRANSFER":
                return "ACCOUNT_TRANSFER";

            case "휴대폰":
            case "MOBILE_PHONE":
                return "MOBILE_PHONE";

            case "가상계좌":
            case "VIRTUAL_ACCOUNT":
                return "VBANK";

            case "간편결제":
            case "EASY_PAY":
                if (responseBody != null) {
                    Object easyPayObj = responseBody.get("easyPay");
                    if (easyPayObj instanceof Map<?, ?> easyMap) {
                        Object provider = easyMap.get("provider");
                        if (provider instanceof String p && !p.isBlank()) {
                            return "EASY_" + p.toUpperCase();
                        }
                    }
                }
                return "EASY_PAY";

            default:
                return tossMethod;
        }
    }

    private String toJsonString(Map<String, Object> map) {
        if (map == null) return null;
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static final long SUBSCRIPTION_DAYS = 30L;

    // 월 요금 BigDecimal 반환
    private BigDecimal getMonthlyPrice(String planCode) {
        if (planCode == null || planCode.isBlank()) {
            return BigDecimal.ZERO;
        }

        SubscriptionPlan plan =
                subscriptionPlanMapper.findActiveByPlanCode(planCode.toUpperCase());

        if (plan == null) {
            throw new IllegalArgumentException("유효하지 않거나 비활성화된 구독 플랜입니다: " + planCode);
        }
        // SubscriptionPlan.monthlyFee 가 BigDecimal 이어야 함
        return plan.getMonthlyFee();
    }

    @Override
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
                    .extraAmount(BigDecimal.ZERO)   // 🔧 BigDecimal로 수정
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
                                .extraAmount(BigDecimal.ZERO)  // 🔧
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
                                .extraAmount(BigDecimal.ZERO)  // 🔧
                                .basicEndDate(null)
                                .build();
                    }

                    // BigDecimal 기반으로 추가금 계산
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
                                .extraAmount(BigDecimal.ZERO)  // 🔧
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
                            .extraAmount(extraAmount)    // 🔧 BigDecimal 그대로
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
                                .extraAmount(BigDecimal.ZERO)  // 🔧
                                .basicEndDate(null)
                                .build()
                );
    }

    // BigDecimal NPE 방지 유틸
    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
