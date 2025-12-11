package kr.or.kosa.backend.admin.dto;

public record LanguageRankingDto(
    String lanuage,
    int submissionCount
) {
    public LanguageRankingDto{
        lanuage = (lanuage == null) ? "TOTAL" : lanuage;
    }
}
