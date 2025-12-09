package kr.or.kosa.backend.admin.dto;

public record PaymentSummaryDto(
    String year,
    String month,
    String paymentCount,
    int totalSales
) {
    public PaymentSummaryDto{
        year = (year == null) ? "TOTAL" : year;
        month = (month == null) ? "-" : month;
    }
}