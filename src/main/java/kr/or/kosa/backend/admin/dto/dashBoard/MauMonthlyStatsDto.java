package kr.or.kosa.backend.admin.dto.dashBoard;

public record MauMonthlyStatsDto(
    int statYear,
    int statMonth,
    long mauCount
) {
}
