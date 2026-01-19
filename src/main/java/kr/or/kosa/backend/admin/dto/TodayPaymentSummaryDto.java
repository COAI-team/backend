package kr.or.kosa.backend.admin.dto;

public record TodayPaymentSummaryDto(
    int todayPaymentCount,
    int todayPaymentTotal
) {
}
