package kr.or.kosa.backend.algorithm.dto.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 사용자 알고리즘 레벨
 * 문제 난이도와 매핑됨
 */
@Getter
@RequiredArgsConstructor
public enum AlgoLevel {
    EMERALD("에메랄드", ProblemDifficulty.BRONZE),
    SAPPHIRE("사파이어", ProblemDifficulty.SILVER),
    RUBY("루비", ProblemDifficulty.GOLD),
    DIAMOND("다이아몬드", ProblemDifficulty.PLATINUM);

    private final String displayName;
    private final ProblemDifficulty matchingDifficulty;

    /**
     * 해당 레벨의 데일리 미션 보상 포인트
     */
    public int getRewardPoints() {
        return switch (this) {
            case EMERALD -> 10;
            case SAPPHIRE -> 20;
            case RUBY -> 30;
            case DIAMOND -> 50;
        };
    }
}
