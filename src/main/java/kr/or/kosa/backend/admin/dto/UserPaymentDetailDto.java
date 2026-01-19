package kr.or.kosa.backend.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UserPaymentDetailDto(
    String orderId,
    long userId,
    String planCode,
    String paymentKey,
    BigDecimal originalAmount,
    BigDecimal usedPoint,
    BigDecimal amount,
    LocalDateTime approvedAt
) {
}
