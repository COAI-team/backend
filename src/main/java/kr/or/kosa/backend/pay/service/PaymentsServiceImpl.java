package kr.or.kosa.backend.pay.service;

import kr.or.kosa.backend.pay.entity.Payments;
import kr.or.kosa.backend.pay.entity.Subscription;
import kr.or.kosa.backend.pay.repository.PaymentsMapper;
import kr.or.kosa.backend.pay.repository.SubscriptionMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import kr.or.kosa.backend.pay.dto.UpgradeQuoteResponse;


import java.time.temporal.ChronoUnit;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class PaymentsServiceImpl implements PaymentsService {

    private final PaymentsMapper paymentsMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final RestTemplate restTemplate;

    // 포인트 서비스
    private final PointService pointService;

    /**
     * application.properties 의 toss.payments.key
     * - test_gsk_... 또는 test_sk_... 형태의 "시크릿 키" 여야 한다.
     */
    @Value("${toss.payments.key}")
    private String secretKey;

    public PaymentsServiceImpl(PaymentsMapper paymentsMapper,
                               SubscriptionMapper subscriptionMapper,
                               PointService pointService) {
        this.paymentsMapper = paymentsMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.pointService = pointService;
        this.restTemplate = new RestTemplate();
    }

    /**
     * 결제 준비 단계에서 DB에 기본 정보 저장
     * - 여기서 status, requestedAt 기본값 세팅
     * - originalAmount / usedPoint / amount 조합 검증
     * - 포인트 잔액 사전 검증까지 수행
     * - ★ 최근 2회 연속 환불 계정은 1개월 동안 결제 차단
     */
    @Override
    public Payments savePayment(Payments payments) {

        // 0-0) userId 필수
        String userId = payments.getUserId();
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("결제를 진행하려면 userId가 필요합니다.");
        }

        // 0-1) 최근 2회 연속 환불 + 30일 이내 → 결제 차단
        if (isUserInRefundBan(userId)) {
            throw new IllegalArgumentException(
                    "최근 2회 연속 환불로 인해 1개월 동안 결제가 제한된 계정입니다.");
        }

        // 0-2) 기본 상태값
        payments.setStatus("READY");
        payments.setRequestedAt(LocalDateTime.now().toString());

        int clientAmount   = payments.getAmount();          // 프론트에서 넘어온 최종 결제 금액
        int originalAmount = payments.getOriginalAmount();  // 플랜 원가
        int usedPoint      = payments.getUsedPoint();       // 사용 포인트

        // 1) 기존 버전 호환:
        //    originalAmount <= 0 && usedPoint == 0 이면
        //    "포인트 미사용 결제"로 간주하고 amount 를 원가로 사용
        if (originalAmount <= 0 && usedPoint == 0) {
            if (clientAmount <= 0) {
                throw new IllegalArgumentException("결제 금액이 올바르지 않습니다.");
            }
            originalAmount = clientAmount;
            payments.setOriginalAmount(originalAmount);
        }

        // 2) 범위 검증
        if (originalAmount <= 0) {
            throw new IllegalArgumentException("원래 결제 금액이 올바르지 않습니다.");
        }
        if (usedPoint < 0) {
            throw new IllegalArgumentException("사용 포인트는 0보다 작을 수 없습니다.");
        }
        if (usedPoint > originalAmount) {
            throw new IllegalArgumentException("사용 포인트가 결제 금액보다 클 수 없습니다.");
        }

        int expectedAmount = originalAmount - usedPoint;
        if (expectedAmount <= 0) {
            throw new IllegalArgumentException("최종 결제 금액이 0 이하가 될 수 없습니다.");
        }

        // 3) 최종 결제 금액 검증
        //    - 클라이언트가 보내준 amount와 서버 계산값이 다르면 거절
        if (clientAmount != 0 && clientAmount != expectedAmount) {
            throw new IllegalArgumentException("요청된 결제 금액과 포인트 적용 금액이 일치하지 않습니다.");
        }

        // 서버 계산값으로 강제 세팅
        payments.setAmount(expectedAmount);

        // 4) 포인트 잔액 사전 검증
        if (usedPoint > 0) {
            // 위에서 userId null/blank 체크 이미 했음
            pointService.validatePointBalance(userId, usedPoint);
        }

        // 5) DB 저장
        paymentsMapper.insertPayment(payments);
        return payments;
    }

    @Override
    public Optional<Payments> getPaymentByOrderId(String orderId) {
        return paymentsMapper.findPaymentByOrderId(orderId);
    }

    @Override
    public List<Subscription> getActiveSubscriptions(String userId) {
        return subscriptionMapper.findActiveSubscriptionsByUserId(userId);
    }

    /**
     * 토스 API 승인 요청 후 DB에 최종 반영 및 구독권 부여
     */
    @Override
    public Payments confirmAndSavePayment(String paymentKey, String orderId, Long amount) {

        // 1. DB에서 현재 결제 상태 조회
        Payments existingPayment = paymentsMapper.findPaymentByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문 정보를 찾을 수 없습니다."));

        // 1-1. 이미 DONE 이면 중복 승인 방지
        if ("DONE".equals(existingPayment.getStatus())) {
            return existingPayment;
        }

        // 1-2. READY 가 아닌 다른 상태면 승인 불가
        if (!"READY".equals(existingPayment.getStatus())) {
            throw new IllegalStateException(
                    "결제 상태가 승인 가능한 상태가 아닙니다. 현재 상태: " + existingPayment.getStatus());
        }

        // 1-3. 서버에 저장된 금액과 요청 금액이 같은지 검증
        if (existingPayment.getAmount() != amount.intValue()) {
            throw new IllegalStateException("요청 금액이 서버에 저장된 금액과 일치하지 않습니다.");
        }

        // 2. 토스 승인 API 호출 준비
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

            if (response.getStatusCode() == HttpStatus.OK &&
                    "DONE".equals(response.getBody().get("status"))) {

                // 3. 결제 성공 시 DB 업데이트
                Payments confirmedPayment = Payments.builder()
                        .paymentKey(paymentKey)
                        .orderId(orderId)
                        .status("DONE")
                        .build();
                paymentsMapper.updatePaymentStatus(confirmedPayment);

                // 3-1. 포인트 실제 차감
                String userId = existingPayment.getUserId();
                int usedPoint = existingPayment.getUsedPoint();
                if (userId != null && !userId.isEmpty() && usedPoint > 0) {
                    pointService.usePoint(userId, usedPoint, orderId);
                }

                // 4. 구독권 활성화
                grantSubscriptionToUser(orderId);

                // 5. 최종 상태 리턴
                return this.getPaymentByOrderId(orderId).orElseThrow(() ->
                        new IllegalStateException("승인되었으나 DB에서 최종 조회 실패"));

            } else {
                String errorMessage = (String) response.getBody().get("message");
                throw new IllegalStateException("토스페이먼츠 승인 거부: " + errorMessage);
            }

        } catch (Exception e) {
            if (e instanceof IllegalStateException || e instanceof IllegalArgumentException) {
                throw e;
            }
            throw new RuntimeException("결제 승인 중 알 수 없는 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * 구독권 부여 로직
     */
    private void grantSubscriptionToUser(String orderId) {
        // 1) 결제 정보 조회
        Payments payment = paymentsMapper.findPaymentByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));

        // 2) 유저 식별 (userId 우선, 없으면 customerName fallback)
        String userId = payment.getUserId();
        if (userId == null || userId.isEmpty()) {
            userId = payment.getCustomerName();
        }

        // 3) 구독 타입 (planCode가 정석, 없으면 orderName 사용)
        String planCode = payment.getPlanCode();
        if (planCode == null || planCode.isEmpty()) {
            planCode = payment.getOrderName(); // 예: "Basic 구독권" 같은 문자열
        }

        LocalDateTime now = LocalDateTime.now();

        // 🔥 4) BASIC → PRO 업그레이드 처리
        // - 결제 planCode가 PRO이고
        // - 해당 유저의 ACTIVE BASIC 구독이 존재하면
        if (userId != null
                && !userId.isEmpty()
                && "PRO".equalsIgnoreCase(planCode)) {

            // 최신 ACTIVE BASIC 하나 찾기
            Optional<Subscription> basicOpt =
                    subscriptionMapper.findLatestActiveSubscriptionByUserIdAndType(userId, "BASIC");

            if (basicOpt.isPresent()) {
                Subscription basicSub = basicOpt.get();

                LocalDateTime basicEnd = basicSub.getEndDate();

                // BASIC 구독의 종료일이 아직 지나지 않았다면 → 업그레이드로 처리
                if (basicEnd != null && basicEnd.isAfter(now)) {

                    // (1) 기존 BASIC 구독 비활성화
                    //     - 상태를 CANCELED로 바꿔서 ACTIVE 목록에서 빠지게
                    subscriptionMapper.updateSubscriptionStatusToCanceled(
                            basicSub.getOrderId(),
                            "CANCELED"
                    );

                    // (2) PRO 구독 생성: "지금 ~ BASIC 종료일"까지만 유효
                    Subscription proSubscription = Subscription.builder()
                            .userId(userId)
                            .orderId(orderId)          // 이번 PRO 결제 orderId
                            .subscriptionType("PRO")   // 명시적으로 PRO
                            .startDate(now)
                            .endDate(basicEnd)         // 🔥 핵심: 새로 30일이 아니라 BASIC 남은 기간만
                            .status("ACTIVE")
                            .build();

                    int inserted = subscriptionMapper.insertSubscription(proSubscription);
                    if (inserted != 1) {
                        throw new RuntimeException("구독권 업그레이드 정보 DB 저장 실패");
                    }

                    // 여기서 바로 return → 아래 "신규 30일 구독" 로직은 실행 안 됨
                    return;
                }
            }
        }

        // 5) 그 외 케이스는 기존처럼 "새 30일 구독" 생성
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
     * 토스페이먼츠 환불/취소 처리
     *  - 결제 후 7일 이내만 환불 가능
     *  - 이미 CANCELED / DONE 이외 상태는 환불 불가
     */
    @Override
    @Transactional
    public Payments cancelPayment(String paymentKey, String cancelReason) {

        Payments paymentToCancel = paymentsMapper.findPaymentByPaymentKey(paymentKey)
                .orElseThrow(() ->
                        new IllegalArgumentException("취소할 결제 정보를 찾을 수 없습니다."));

        // 이미 취소된 건은 비즈니스 에러로 처리
        if ("CANCELED".equals(paymentToCancel.getStatus())) {
            throw new IllegalStateException("이미 취소된 결제입니다.");
        }

        // 결제 완료 상태(DONE)만 취소 허용
        if (!"DONE".equals(paymentToCancel.getStatus())) {
            throw new IllegalStateException(
                    "결제 완료 상태에서만 환불할 수 있습니다. (현재 상태: " + paymentToCancel.getStatus() + ")");
        }

        // ★ 7일 이내 결제만 환불 허용
        LocalDateTime requestedAt = parseDateTime(paymentToCancel.getRequestedAt());
        if (requestedAt != null && requestedAt.isBefore(LocalDateTime.now().minusDays(7))) {
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
                String newStatus = (String) response.getBody().get("status"); // CANCELED or PARTIAL_CANCELED

                // 결제 상태/취소일 업데이트
                paymentsMapper.updatePaymentStatusToCanceled(paymentToCancel.getOrderId(), newStatus);
                // 구독 상태도 CANCELED 처리
                subscriptionMapper.updateSubscriptionStatusToCanceled(paymentToCancel.getOrderId(), "CANCELED");

                // 포인트 환불 처리
                String userId = paymentToCancel.getUserId();
                int usedPoint = paymentToCancel.getUsedPoint();
                if (userId != null && !userId.isEmpty() && usedPoint > 0) {
                    pointService.refundPoint(userId, usedPoint, paymentToCancel.getOrderId(), cancelReason);
                }

                // ★ DB에서 최신 상태(취소 시간 포함)를 다시 읽어서 응답
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

    /**
     * 최근 2회 연속 환불 + 30일 이내인지 체크
     *  - paymentsMapper.findRecentPaymentsByUser(userId, 2) 사용
     */
    private boolean isUserInRefundBan(String userId) {
        List<Payments> recent = paymentsMapper.findRecentPaymentsByUser(userId, 2);
        if (recent == null || recent.size() < 2) {
            return false;
        }

        Payments latest   = recent.get(0); // 가장 최근
        Payments previous = recent.get(1); // 그 이전

        // 둘 다 취소 상태가 아니면 연속 환불 아님
        if (!"CANCELED".equals(latest.getStatus())
                || !"CANCELED".equals(previous.getStatus())) {
            return false;
        }

        // 최근 결제 요청 시간 기준으로 30일 이내인지 확인
        LocalDateTime latestRequestedAt = parseDateTime(latest.getRequestedAt());
        if (latestRequestedAt == null) {
            return false;
        }

        return latestRequestedAt.isAfter(LocalDateTime.now().minusDays(30));
    }

    /**
     * DB/엔티티에서 가져온 날짜 문자열을 LocalDateTime으로 변환
     *  - "2025-11-24T12:34:56.789" (LocalDateTime.toString()) 형식
     *  - "2025-11-24 12:34:56" (MySQL DATETIME) 형식 둘 다 대응
     */
    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;

        try {
            // LocalDateTime.now().toString() 형태 (예: 2025-11-24T12:34:56.789)
            if (value.contains("T")) {
                return LocalDateTime.parse(value);
            }

            // MySQL DATETIME 형태 (예: 2025-11-24 12:34:56 or 2025-11-24 12:34:56.0)
            String trimmed = value;
            if (value.length() >= 19) {
                trimmed = value.substring(0, 19); // "yyyy-MM-dd HH:mm:ss"
            }
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return LocalDateTime.parse(trimmed, fmt);
        } catch (Exception e) {
            // 파싱 실패 시 null 리턴해서 제한 로직에서 그냥 무시하도록
            return null;
        }
    }

    // 구독을 "한 달"로 볼 때 기준 일수
    private static final long SUBSCRIPTION_DAYS = 30L;

    // 플랜 월 요금 (TODO: 나중에 DB나 설정으로 빼도 됨)
    private int getMonthlyPrice(String planCode) {
        if (planCode == null) return 0;
        switch (planCode.toUpperCase()) {
            case "BASIC":
                return 39800;
            case "PRO":
                return 42900;
            default:
                return 0; // 정의되지 않은 플랜
        }
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

        // 지금은 BASIC → PRO만 업그레이드로 취급
        if (!"PRO".equals(normalizedTarget)) {
            return UpgradeQuoteResponse.builder()
                    .upgrade(false)
                    .fromPlan(null)
                    .toPlan(normalizedTarget)
                    .usedDays(0)
                    .remainingDays(0)
                    .extraAmount(0)
                    .basicEndDate(null)
                    .build();
        }

        // 최신 ACTIVE BASIC 구독 1개 조회
        return subscriptionMapper.findLatestActiveSubscriptionByUserIdAndType(userId, "BASIC")
                .map(basicSub -> {

                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime start = basicSub.getStartDate();
                    LocalDateTime end = basicSub.getEndDate();

                    // 이미 끝난 BASIC이면 업그레이드 대상 아님
                    if (end == null || !end.isAfter(now)) {
                        return UpgradeQuoteResponse.builder()
                                .upgrade(false)
                                .fromPlan("BASIC")
                                .toPlan("PRO")
                                .usedDays(0)
                                .remainingDays(0)
                                .extraAmount(0)
                                .basicEndDate(null)
                                .build();
                    }

                    // 전체 구독 기간(일수) 계산
                    long totalDays = 0;
                    if (start != null && end != null) {
                        totalDays = ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate());
                    }
                    if (totalDays <= 0) {
                        totalDays = SUBSCRIPTION_DAYS; // 안전장치
                    }

                    // 사용한 일수
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
                                .extraAmount(0)
                                .basicEndDate(null)
                                .build();
                    }

                    int basicPrice = getMonthlyPrice("BASIC");
                    int proPrice   = getMonthlyPrice("PRO");
                    int diff       = proPrice - basicPrice;

                    if (diff <= 0) {
                        // 말이 안 되는 설정이면 그냥 업그레이드 없음 처리
                        return UpgradeQuoteResponse.builder()
                                .upgrade(false)
                                .fromPlan("BASIC")
                                .toPlan("PRO")
                                .usedDays(usedDays)
                                .remainingDays(remainingDays)
                                .extraAmount(0)
                                .basicEndDate(null)
                                .build();
                    }

                    // 프리로레이션: 월 차액 * (남은일수 / 전체일수)
                    double rawExtra = diff * (remainingDays / (double) totalDays);
                    int extraAmount = (int) Math.ceil(rawExtra); // 필요하면 10원 단위 반올림도 가능

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
                        // ACTIVE BASIC 구독이 없으면 업그레이드 상황 아님
                        UpgradeQuoteResponse.builder()
                                .upgrade(false)
                                .fromPlan(null)
                                .toPlan("PRO")
                                .usedDays(0)
                                .remainingDays(0)
                                .extraAmount(0)
                                .basicEndDate(null)
                                .build()
                );
    }
}
