package kr.or.kosa.backend.pay.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.pay.dto.TossConfirmResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

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
        } catch (Exception e) {
            throw new RuntimeException("결제 승인 중 알 수 없는 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * 환불 요청 – 성공 시 새 status("CANCELED" 등)을 반환
     */
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
