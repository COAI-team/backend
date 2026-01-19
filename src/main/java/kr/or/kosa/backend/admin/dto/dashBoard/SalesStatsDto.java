package kr.or.kosa.backend.admin.dto.dashBoard;

import java.math.BigDecimal;

public record SalesStatsDto(
    int statYear,
    int statMonth,
    long paymentCount,
    BigDecimal revenue
) {
}
