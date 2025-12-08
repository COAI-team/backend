package kr.or.kosa.backend.admin.dto.response;

public record AdminDashBoardUserCountResponseDto(
    String year,
    String month,
    int userCount
) {
    public AdminDashBoardUserCountResponseDto(
        String year,
        String month,
        int userCount
    ){
        this.year = year == null ? "TOTAL" : year;
        this.month = month == null ? "-" : month;
        this.userCount = userCount;
    }
}
