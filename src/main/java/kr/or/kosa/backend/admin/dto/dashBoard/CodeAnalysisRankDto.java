package kr.or.kosa.backend.admin.dto.dashBoard;

public record CodeAnalysisRankDto(
    int statYear,
    int statMonth,
    int ranking,
    String userEmail,
    String userNickName,
    long analysisCount
) {
}
