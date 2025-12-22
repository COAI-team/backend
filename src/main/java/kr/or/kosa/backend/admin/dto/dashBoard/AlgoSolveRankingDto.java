package kr.or.kosa.backend.admin.dto.dashBoard;

public record AlgoSolveRankingDto(
    int statYear,
    int statMonth,
    int ranking,
    String userEmail,
    String userNickName,
    long solveCount
) {
}
