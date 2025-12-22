package kr.or.kosa.backend.admin.dto.dashBoard;

import java.time.LocalDate;
// 전날 데이터
public record SummarySection(
    LocalDate statDate,
    int totalUsers,
    int newUsers,
    int paymentCount,
    long revenue
) {
}
