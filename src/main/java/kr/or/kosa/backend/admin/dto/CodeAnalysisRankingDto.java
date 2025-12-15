package kr.or.kosa.backend.admin.dto;

public record CodeAnalysisRankingDto(
    long userId,
    String userNickName,
    String codeAnalysisCount
) {
}
