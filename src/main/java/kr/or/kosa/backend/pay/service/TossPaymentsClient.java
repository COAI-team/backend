package kr.or.kosa.backend.pay.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.pay.dto.TossConfirmResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpServerErrorException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

@Service
public class TossPaymentsClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String secretKey;

    public TossPaymentsClient(@Value("${toss.payments.key}") String secretKey,
                              ObjectMapper objectMapper) {
        this.secretKey = secretKey;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    public boolean getPayment(String paymentKey) {
        HttpHeaders headers = createHeaders();
        String url = "https://api.tosspayments.com/v1/payments/" + paymentKey;
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
            String status = (String) responseBody.get("status");
            return "DONE".equals(status);// DTO 변환
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) { // 404 NOT_FOUND => 결제 데이터가 없다면
                return false;
            }
            throw new IllegalStateException("결제 단건 조회 실패: " + e.getResponseBodyAsString());
        }
    }


    public TossConfirmResult confirmPayment(String paymentKey, String orderId, long amount) {

        HttpHeaders headers = createHeaders();
        Map<String, Object> body = Map.of(
                "paymentKey", paymentKey,
                "orderId", orderId,
                "amount", amount
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String url = "https://api.tosspayments.com/v1/payments/confirm";

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();

            if (!response.getStatusCode().is2xxSuccessful() || responseBody == null) {
                throw new IllegalStateException("토스페이먼츠 승인 실패: HTTP " + response.getStatusCode());
            }

            String status = (String) responseBody.get("status");
            if (!"DONE".equals(status)) {
                String msg = String.valueOf(responseBody.getOrDefault("message", "unknown error"));
                throw new IllegalStateException("토스페이먼츠 승인 거부: " + msg);
            }

            String tossMethod = (String) responseBody.get("method");
            String internalPayMethod = convertTossMethodToInternal(tossMethod, responseBody);

            Map<String, Object> cardMap = null;
            Object cardObj = responseBody.get("card");
            if (cardObj instanceof Map<?, ?> m) {
                //noinspection unchecked
                cardMap = (Map<String, Object>) m;
            }

            String cardCompany = null;
            String approveNo   = null;
            if (cardMap != null) {
                cardCompany = (String) cardMap.get("issuerCode");
                approveNo   = (String) cardMap.get("approveNo");
            }

            String approvedAtRaw = (String) responseBody.get("approvedAt");
            LocalDateTime approvedAt = null;
            if (approvedAtRaw != null) {
                approvedAt = OffsetDateTime.parse(approvedAtRaw)
                        .atZoneSameInstant(ZoneId.of("Asia/Seoul"))
                        .toLocalDateTime();
            }

            String rawJson = toJsonString(responseBody);

            return TossConfirmResult.builder()
                    .status(status)
                    .payMethod(internalPayMethod)
                    .rawJson(rawJson)
                    .cardCompany(cardCompany)
                    .approveNo(approveNo)
                    .approvedAt(approvedAt)
                    .build();

        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("토스페이먼츠 승인 거부: " + e.getResponseBodyAsString(), e);
        } catch (HttpServerErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            if (responseBody != null &&
                    (responseBody.contains("FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING")
                            || responseBody.contains("S008")
                            || responseBody.contains("기존 요청"))) {
                throw new IllegalStateException("결제 승인 처리 중입니다. 잠시 후 다시 시도해 주세요.", e);
            }
            throw new RuntimeException("토스페이먼츠 승인 중 서버 오류가 발생했습니다: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("결제 승인 중 알 수 없는 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * 환불 요청 – 성공 시 새 status("CANCELED" 등)을 반환
     */
    public Map<String, Object> inquirePayment(String paymentKey, String orderId) {
        String url;
        if (paymentKey != null && !paymentKey.isBlank()) {
            url = String.format("https://api.tosspayments.com/v1/payments/%s", paymentKey);
        } else if (orderId != null && !orderId.isBlank()) {
            url = String.format("https://api.tosspayments.com/v1/payments/orders/%s", orderId);
        } else {
            throw new IllegalArgumentException("paymentKey 또는 orderId 중 하나는 필수입니다.");
        }

        HttpHeaders headers = createHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();

            if (!response.getStatusCode().is2xxSuccessful() || responseBody == null) {
                throw new IllegalStateException("토스 단건조회 응답이 유효하지 않습니다: HTTP " + response.getStatusCode());
            }

            return responseBody;

        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("토스 단건조회 실패: " + e.getResponseBodyAsString(), e);
        } catch (HttpServerErrorException e) {
            throw new IllegalStateException("토스 단건조회 서버 오류: " + e.getResponseBodyAsString(), e);
        }
    }

    public String cancelPayment(String paymentKey, String cancelReason) {
        HttpHeaders headers = createHeaders();
        Map<String, Object> body = Map.of("cancelReason", cancelReason);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String url = String.format("https://api.tosspayments.com/v1/payments/%s/cancel", paymentKey);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();

            if (!response.getStatusCode().is2xxSuccessful() || responseBody == null) {
                throw new IllegalStateException("토스페이먼츠 환불 요청 실패: HTTP " + response.getStatusCode());
            }

            return (String) responseBody.get("status");

        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("토스페이먼츠 환불 거부: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RuntimeException("환불 처리 중 알 수 없는 오류 발생", e);
        }
    }

    // ================== 내부 헬퍼 ==================

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(secretKey, "");
        return headers;
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
                        Object provider = ((Map<?, ?>) easyMap).get("provider");
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
}
