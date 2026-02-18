package kr.or.kosa.backend.admin.dto.dashBoard;

public record LanguageRankDto(
    int statYear,
    int statMonth,
    String languageName,
    long usageCount
) {
}
