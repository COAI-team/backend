package kr.or.kosa.backend.Pay.service;

import kr.or.kosa.backend.Pay.entity.Payments;
import kr.or.kosa.backend.Pay.entity.Subscription;
import kr.or.kosa.backend.Pay.repository.PaymentsMapper;
import kr.or.kosa.backend.Pay.repository.SubscriptionMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class PaymentsServiceImpl implements PaymentsService {

    private final PaymentsMapper paymentsMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final RestTemplate restTemplate;

    @Value("${toss.payments.key}")
    private String secretKey;

    public PaymentsServiceImpl(PaymentsMapper paymentsMapper, SubscriptionMapper subscriptionMapper) {
        this.paymentsMapper = paymentsMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public Payments savePayment(Payments payments) {
        payments.setStatus("READY");
        payments.setRequestedAt(LocalDateTime.now().toString()); // 혹은 포맷 지정
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
     * **핵심 로직**: 토스 API 승인 요청 후 DB에 최종 반영 및 구독권 부여
     */
    @Override
    public Payments confirmAndSavePayment(String paymentKey, String orderId, Long amount) {

        // 1. DB에서 현재 결제 상태 조회 및 중복 승인 방지
        Payments existingPayment = paymentsMapper.findPaymentByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문 정보를 찾을 수 없습니다."));

        if (existingPayment.getAmount() != amount.intValue()) {
            throw new IllegalStateException("요청 금액이 서버에 저장된 금액과 일치하지 않습니다.");
        }

        if ("DONE".equals(existingPayment.getStatus())) {
            // 중복 승인 방지: 이미 완료된 결제는 재승인 없이 성공 정보를 반환합니다.
            return existingPayment;
        }

        // 2. 요청 헤더 및 바디 설정 (토스 API 호출 준비)
        String encodedSecretKey = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(encodedSecretKey);

        Map<String, Object> body = new HashMap<>();
        body.put("paymentKey", paymentKey);
        body.put("orderId", orderId);
        body.put("amount", amount);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String url = "https://api.tosspayments.com/v1/payments/confirm";

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && "DONE".equals(response.getBody().get("status"))) {

                // 3. 결제 성공 시 DB 업데이트 (Payments 테이블)
                Payments confirmedPayment = Payments.builder()
                        .paymentKey(paymentKey)
                        .orderId(orderId)
                        .status("DONE")
                        .build();
                paymentsMapper.updatePaymentStatus(confirmedPayment);

                // 4. 구독권 활성화 로직 호출 (Subscription 테이블)
                grantSubscriptionToUser(orderId);

                return this.getPaymentByOrderId(orderId).orElseThrow(() ->
                        new IllegalStateException("승인되었으나 DB에서 최종 조회 실패"));

            } else {
                // 토스 승인 거부 (예: 금액 불일치 등)
                String errorMessage = (String) response.getBody().get("message");
                throw new IllegalStateException("토스페이먼츠 승인 거부: " + errorMessage);
            }

        } catch (Exception e) {
            // 통신 오류, DB 오류, 구독권 부여 실패 등 모든 예외 처리
            if (e instanceof IllegalStateException || e instanceof IllegalArgumentException) {
                throw e;
            }
            throw new RuntimeException("결제 승인 중 알 수 없는 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * 구독권 부여 로직: Subscription 테이블에 기록을 추가합니다.
     */
    private void grantSubscriptionToUser(String orderId) {
        Payments payment = paymentsMapper.findPaymentByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));

        // 구독 기간 설정 (모든 플랜 1개월 가정)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = now.plusMonths(1);

        Subscription newSubscription = Subscription.builder()
                .userId(payment.getCustomerName()) // NOTE: 실제 User ID로 대체 필요
                .orderId(orderId)
                .subscriptionType(payment.getOrderName())
                .startDate(now)
                .endDate(endDate)
                .status("ACTIVE")
                .build();

        int result = subscriptionMapper.insertSubscription(newSubscription);
        if (result != 1) {
            // DB 저장 실패 시 트랜잭션 롤백 유도
            throw new RuntimeException("구독권 정보 DB 저장 실패");
        }
    }

    @Override
    @Transactional
    public Payments cancelPayment(String paymentKey, String cancelReason) {

        // 1. DB에서 결제 정보 조회 (paymentKey는 토스 API의 식별자입니다)
        Payments paymentToCancel = paymentsMapper.findPaymentByPaymentKey(paymentKey)
                .orElseThrow(() -> new IllegalArgumentException("취소할 결제 정보를 찾을 수 없습니다."));

        // 2. 이미 취소된 결제인지 확인
        if ("CANCELED".equals(paymentToCancel.getStatus())) {
            return paymentToCancel; // 이미 취소되었으면 해당 정보 반환
        }

        // 3. 토스페이먼츠 환불 API 호출
        String encodedSecretKey = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(encodedSecretKey);

        // 환불 요청 바디 (취소 사유 필수)
        Map<String, Object> body = new HashMap<>();
        body.put("cancelReason", cancelReason);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        // 토스 환불 API URL: /payments/{paymentKey}/cancel
        String url = String.format("https://api.tosspayments.com/v1/payments/%s/cancel", paymentKey);

        try {
            // 토스 API 호출 및 응답 받기
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                // 4. 환불 성공 시 DB 상태 업데이트
                String newStatus = (String) response.getBody().get("status"); // CANCELED 또는 PARTIAL_CANCELED

                paymentsMapper.updatePaymentStatusToCanceled(paymentToCancel.getOrderId(), newStatus);
                subscriptionMapper.updateSubscriptionStatusToCanceled(paymentToCancel.getOrderId(), "CANCELED");

                paymentToCancel.setStatus(newStatus); // 반환 객체 업데이트
                return paymentToCancel;
            } else {
                throw new IllegalStateException("토스페이먼츠 환불 요청 실패: HTTP " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            // 토스 API에서 비즈니스 오류 응답(4xx)을 받은 경우
            throw new IllegalStateException("토스페이먼츠 환불 거부: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            // 통신 오류, DB 오류 등
            throw new RuntimeException("환불 처리 중 알 수 없는 오류 발생", e);
        }
    }
}