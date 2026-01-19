package kr.or.kosa.backend.admin.dto;

public record UserCountSummaryDto(
    String year,
    String month,
    int userCount
) {
    public UserCountSummaryDto {
        year = (year == null) ? "TOTAL" : year;
        month = (month == null) ? "-" : month;
    }
}
