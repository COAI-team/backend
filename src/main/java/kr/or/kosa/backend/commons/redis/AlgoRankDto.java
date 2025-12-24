package kr.or.kosa.backend.commons.redis;

public record AlgoRankDto(
    int rank,
    long userId,
    String nickname,
    double score
) {}
