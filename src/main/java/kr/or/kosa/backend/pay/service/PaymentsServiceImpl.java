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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     */
    @Override
    public Payments savePayment(Payments payments) {

        // 0) 기본 상태값
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
            String userId = payments.getUserId();
            if (userId == null || userId.isBlank()) {
                throw new IllegalArgumentException("포인트를 사용하려면 userId가 필요합니다.");
            }

            // 여기서는 실제 차감은 하지 않고, "충분한지"만 확인
            pointService.validatePointBalance(userId, usedPoint);
        }

        // 5) DB 저장
        paymentsMapper.insertPayment(payments);
        return payments;
    }

    @Override
    public java.util.Optional<Payments> getPaymentByOrderId(String orderId) {
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
        Payments payment = paymentsMapper.findPaymentByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));

        String userId = payment.getUserId();
        if (userId == null || userId.isEmpty()) {
            userId = payment.getCustomerName();
        }

        String subscriptionType = payment.getPlanCode();
        if (subscriptionType == null || subscriptionType.isEmpty()) {
            subscriptionType = payment.getOrderName();
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = now.plusMonths(1);

        Subscription newSubscription = Subscription.builder()
                .userId(userId)
                .orderId(orderId)
                .subscriptionType(subscriptionType)
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
     */
    @Override
    @Transactional
    public Payments cancelPayment(String paymentKey, String cancelReason) {

        Payments paymentToCancel = paymentsMapper.findPaymentByPaymentKey(paymentKey)
                .orElseThrow(() -> new IllegalArgumentException("취소할 결제 정보를 찾을 수 없습니다."));

        if ("CANCELED".equals(paymentToCancel.getStatus())) {
            return paymentToCancel;
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

                paymentsMapper.updatePaymentStatusToCanceled(paymentToCancel.getOrderId(), newStatus);
                subscriptionMapper.updateSubscriptionStatusToCanceled(paymentToCancel.getOrderId(), "CANCELED");

                // 포인트 환불 처리
                String userId = paymentToCancel.getUserId();
                int usedPoint = paymentToCancel.getUsedPoint();
                if (userId != null && !userId.isEmpty() && usedPoint > 0) {
                    pointService.refundPoint(userId, usedPoint, paymentToCancel.getOrderId(), cancelReason);
                }

                paymentToCancel.setStatus(newStatus);
                return paymentToCancel;
            } else {
                throw new IllegalStateException("토스페이먼츠 환불 요청 실패: HTTP " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("토스페이먼츠 환불 거부: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RuntimeException("환불 처리 중 알 수 없는 오류 발생", e);
        }
    }
}
