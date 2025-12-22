package kr.or.kosa.backend.admin.dto.dashBoard;

public record AnalysisTypeMonthlyStatsDto(
    int statYear,
    int statMonth,
    String analysisType,
    long analysisCount
) {
}
