package kr.or.kosa.backend.admin.dto;

public record AlgoSolverRankingDto(
    long userId,
    String userNickName,
    int AlgoSolveCount
) {}
