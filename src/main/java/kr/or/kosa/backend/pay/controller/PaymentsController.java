package kr.or.kosa.backend.pay.controller;

import kr.or.kosa.backend.pay.dto.UpgradeQuoteResponse;
import kr.or.kosa.backend.pay.entity.Payments;
import kr.or.kosa.backend.pay.entity.Subscription;
import kr.or.kosa.backend.pay.service.PaymentsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/payments")
@CrossOrigin(origins = "http://localhost:5173")
public class PaymentsController {

    private final PaymentsService paymentsService;

    public PaymentsController(PaymentsService paymentsService) {
        this.paymentsService = paymentsService;
    }

    /**
     * 1. 결제 위젯을 띄우기 전에 초기 결제 정보를 DB에 저장 (READY 상태)
     *
     * 프론트에서 넘어오는 JSON 예시:
     * {
     *   "orderId": "sub_...",
     *   "userId": "USER_001",
     *   "planCode": "BASIC",
     *   "orderName": "Basic 구독 1개월",
     *   "customerName": "홍길동",
     *   "originalAmount": 39800,
     *   "usedPoint": 5000,
     *   "amount": 34800
     * }
     */
    @PostMapping("/ready")
    public ResponseEntity<Object> createPaymentReady(@RequestBody Payments payments) {

        try {
            System.out.println("### [Ready] 요청 시작");
            System.out.println(" - orderId          : " + payments.getOrderId());
            System.out.println(" - userId           : " + payments.getUserId());
            System.out.println(" - planCode         : " + payments.getPlanCode());
            System.out.println(" - originalAmount   : " + payments.getOriginalAmount());
            System.out.println(" - usedPoint        : " + payments.getUsedPoint());
            System.out.println(" - final amount     : " + payments.getAmount());

            Payments readyPayment = paymentsService.savePayment(payments);

            System.out.println("### [Ready] DB 저장 성공.");
            return ResponseEntity.status(HttpStatus.CREATED).body(readyPayment);

        } catch (IllegalArgumentException e) {
            // 포인트 부족, 잘못된 사용량 등 비즈니스 검증 실패
            System.err.println("### [Ready] 요청 검증 실패: " + e.getMessage());

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("code", "PAYMENT_READY_VALIDATION_ERROR");
            errorResponse.put("message", e.getMessage());

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(errorResponse);

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("### [Ready] 요청 처리 중 심각한 오류 발생: " + e.getMessage());

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("code", "DB_SAVE_ERROR");
            errorResponse.put("message", "결제 준비 중 서버 내부 오류 (DB 저장 실패).");

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }
    }

    /**
     * 2. 결제 성공 후 프론트엔드에서 최종 승인을 요청하는 엔드포인트
     *    - Toss 예제처럼 JSON body 로 { paymentKey, orderId, amount } 받음
     */
    @PostMapping("/confirm")
    public ResponseEntity<Object> confirmPayment(@RequestBody Map<String, Object> request) {
        try {
            String paymentKey = (String) request.get("paymentKey");
            String orderId = (String) request.get("orderId");

            if (paymentKey == null || orderId == null) {
                throw new IllegalArgumentException("paymentKey와 orderId는 필수값입니다.");
            }

            Object amountObj = request.get("amount");
            Long amount;
            if (amountObj instanceof Number) {
                amount = ((Number) amountObj).longValue();
            } else if (amountObj instanceof String) {
                amount = Long.valueOf((String) amountObj);
            } else {
                throw new IllegalArgumentException("amount 값이 올바르지 않습니다.");
            }

            Payments confirmedPayment =
                    paymentsService.confirmAndSavePayment(paymentKey, orderId, amount);
            return ResponseEntity.ok(confirmedPayment);

        } catch (IllegalStateException | IllegalArgumentException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("code", "PAYMENT_BUSINESS_ERROR");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);

        } catch (RuntimeException e) {
            e.printStackTrace();
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("code", "INTERNAL_SERVER_ERROR");
            errorResponse.put("message", "결제 최종 승인 중 알 수 없는 서버 오류가 발생했습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 3. 특정 사용자의 활성 구독 목록 조회
     */
    @GetMapping("/subscriptions/{userId}")
    public ResponseEntity<?> getSubscriptions(@PathVariable String userId) {
        try {
            List<Subscription> subscriptions = paymentsService.getActiveSubscriptions(userId);

            // NPE 방지용
            if (subscriptions == null) {
                subscriptions = Collections.emptyList();
            }

            // ❗ 항상 200 OK + JSON 배열([] 또는 [ ... ]) 리턴
            return ResponseEntity.ok(subscriptions);

        } catch (Exception e) {
            e.printStackTrace();

            // 에러도 JSON으로 넘겨주면 프론트에서 처리하기 편함
            Map<String, String> error = new HashMap<>();
            error.put("code", "SUBSCRIPTION_FETCH_ERROR");
            error.put("message", "구독 정보를 조회하는 중 서버 오류가 발생했습니다.");

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error);
        }
    }


    /**
     * 4. 결제 취소(환불)
     *    예) POST /payments/cancel?paymentKey=xxx&cancelReason=테스트취소
     */
    @PostMapping("/cancel")
    public ResponseEntity<Object> cancelPayment(
            @RequestParam String paymentKey,
            @RequestParam String cancelReason) {

        try {
            Payments canceledPayment = paymentsService.cancelPayment(paymentKey, cancelReason);
            return ResponseEntity.ok(canceledPayment);

        } catch (IllegalStateException | IllegalArgumentException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("code", "CANCEL_BUSINESS_ERROR");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);

        } catch (RuntimeException e) {
            e.printStackTrace();
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("code", "INTERNAL_SERVER_ERROR");
            errorResponse.put("message", "환불 처리 중 알 수 없는 서버 오류가 발생했습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * BASIC → PRO 업그레이드 시, 남은 일수 기준 추가 결제 금액 안내
     *
     * 예: GET /payments/upgrade-quote?userId=TEST_USER_001&planCode=PRO
     */
    @GetMapping("/upgrade-quote")
    public ResponseEntity<?> getUpgradeQuote(
            @RequestParam String userId,
            @RequestParam String planCode
    ) {
        try {
            UpgradeQuoteResponse quote = paymentsService.getUpgradeQuote(userId, planCode);
            return ResponseEntity.ok(quote);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "code", "UPGRADE_QUOTE_VALIDATION_ERROR",
                            "message", e.getMessage()
                    ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "code", "UPGRADE_QUOTE_INTERNAL_ERROR",
                            "message", "업그레이드 견적 계산 중 서버 오류가 발생했습니다."
                    ));
        }
    }
}
