package kr.or.kosa.backend.admin.dto.dashBoard;

public record UserStatsDto(
    int statYear,
    int statMonth,
    long newUserCount,
    long totalUserCount

) {
}
