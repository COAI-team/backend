package kr.or.kosa.backend.admin.dto.response;

public record AdminDashBoardUserCountResponseDto(
    String year,
    String month,
    int userCount
) {
    public AdminDashBoardUserCountResponseDto{
        year = (year == null) ? "TOTAL" : year;
        month = (month == null) ? "-" : month;
    }
}
