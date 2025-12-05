package kr.or.kosa.backend.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MyPaymentDetailDto(
    long userId,
    String planCode,
    BigDecimal amount,
    LocalDateTime approvedAt
) {
}
