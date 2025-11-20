package kr.or.kosa.backend.Pay.controller;

import kr.or.kosa.backend.Pay.entity.Payments;
import kr.or.kosa.backend.Pay.entity.Subscription;
import kr.or.kosa.backend.Pay.service.PaymentsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
     */
    @PostMapping("/ready")
    public ResponseEntity<Object> createPaymentReady(@RequestBody Payments payments) { // **[수정]** ResponseEntity<Object>로 변경
        // ⭐ Ready 요청 처리 메서드에 try-catch 블록을 추가합니다.
        try {
            System.out.println("### [Ready] 요청 시작. OrderId: " + payments.getOrderId());

            // 기존 서비스 호출 로직
            Payments readyPayment = paymentsService.savePayment(payments);

            System.out.println("### [Ready] DB 저장 성공.");
            return ResponseEntity.status(HttpStatus.CREATED).body(readyPayment);

        } catch (Exception e) {
            // ⭐ ⭐ ⭐ 예외를 강제로 출력하여 로그에 찍히게 합니다. ⭐ ⭐ ⭐
            e.printStackTrace();
            System.err.println("### [Ready] 요청 처리 중 심각한 오류 발생: " + e.getMessage());

            // 프론트엔드에서 실패 응답을 처리할 수 있도록 500 응답 반환
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("code", "DB_SAVE_ERROR");
            errorResponse.put("message", "결제 준비 중 서버 내부 오류 (DB 저장 실패).");

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse); // **[수정]** 오류 응답 본문 반환
        }
    }

    /**
     * 2. 결제 성공 후 프론트엔드에서 최종 승인을 요청하는 엔드포인트
     */
    @PostMapping("/confirm")
    public ResponseEntity<Object> confirmPayment(
            @RequestParam String paymentKey,
            @RequestParam String orderId,
            @RequestParam Long amount) {
        try {
            // Service의 핵심 로직 호출
            Payments confirmedPayment = paymentsService.confirmAndSavePayment(paymentKey, orderId, amount);

            // 성공 응답: 결제 정보와 200 OK 반환
            return ResponseEntity.ok(confirmedPayment);

        } catch (IllegalStateException | IllegalArgumentException e) {
            // 비즈니스 로직 오류 (400 Bad Request): 토스 승인 거부, 이미 완료된 결제, 잘못된 orderId 등
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("code", "PAYMENT_BUSINESS_ERROR");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);

        } catch (RuntimeException e) {
            // 서버 내부 오류 (500 Internal Server Error): DB 오류, 통신 오류 등
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
    public ResponseEntity<List<Subscription>> getSubscriptions(@PathVariable String userId) {
        try {
            List<Subscription> subscriptions = paymentsService.getActiveSubscriptions(userId);

            if (subscriptions.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
            }
            return ResponseEntity.ok(subscriptions);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PostMapping("/cancel")
    public ResponseEntity<Object> cancelPayment(
            @RequestParam String paymentKey,
            @RequestParam(required = true) String cancelReason) {

        try {
            Payments canceledPayment = paymentsService.cancelPayment(paymentKey, cancelReason);

            // 200 OK와 취소된 결제 정보를 반환
            return ResponseEntity.ok(canceledPayment);

        } catch (IllegalStateException | IllegalArgumentException e) {
            // 비즈니스 로직 오류 (예: 이미 취소됨, 유효하지 않은 키)
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("code", "CANCEL_BUSINESS_ERROR");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);

        } catch (RuntimeException e) {
            // 서버 내부 오류 (500)
            e.printStackTrace();
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("code", "INTERNAL_SERVER_ERROR");
            errorResponse.put("message", "환불 처리 중 알 수 없는 서버 오류가 발생했습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}