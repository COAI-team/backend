package kr.or.kosa.backend.algorithm.dto.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 사용자 알고리즘 레벨
 * 문제 난이도와 매핑됨
 *
 * 변경사항 (2025-12-17): XP 기반 레벨 시스템 도입
 * - requiredXp 필드 추가: 해당 레벨에 도달하기 위한 최소 XP
 * - fromXp() 메서드 추가: XP로 레벨 산정
 * - 레벨 임계값: EMERALD(0), SAPPHIRE(300), RUBY(1000), DIAMOND(3000)
 */
@Getter
@RequiredArgsConstructor
public enum AlgoLevel {
    EMERALD("에메랄드", 0, ProblemDifficulty.BRONZE, 10),
    SAPPHIRE("사파이어", 300, ProblemDifficulty.SILVER, 15),
    RUBY("루비", 1000, ProblemDifficulty.GOLD, 25),
    DIAMOND("다이아몬드", 3000, ProblemDifficulty.PLATINUM, 40);

    /**
     * 한국어 표시명
     */
    private final String displayName;

    /**
     * 해당 레벨에 도달하기 위한 최소 XP
     */
    private final int requiredXp;

    /**
     * 데일리 미션에서 배정되는 문제 난이도
     */
    private final ProblemDifficulty matchingDifficulty;

    /**
     * 데일리 미션 완료 시 보상 포인트
     */
    private final int rewardPoints;

    /**
     * XP로 레벨 산정
     * 가장 높은 레벨부터 역순으로 검사하여 해당 레벨 반환
     *
     * @param xp 총 경험치
     * @return 해당 XP에 맞는 레벨
     */
    public static AlgoLevel fromXp(int xp) {
        // 높은 레벨부터 역순으로 검사
        AlgoLevel[] levels = values();
        for (int i = levels.length - 1; i >= 0; i--) {
            if (xp >= levels[i].requiredXp) {
                return levels[i];
            }
        }
        return EMERALD; // 기본값
    }

    /**
     * 다음 레벨까지 필요한 XP 계산
     *
     * @param currentXp 현재 XP
     * @return 다음 레벨까지 필요한 XP (최고 레벨이면 0 반환)
     */
    public int getXpToNextLevel(int currentXp) {
        AlgoLevel nextLevel = getNextLevel();
        if (nextLevel == this) {
            return 0; // 최고 레벨
        }
        return nextLevel.requiredXp - currentXp;
    }

    /**
     * 다음 레벨 반환
     */
    public AlgoLevel getNextLevel() {
        return switch (this) {
            case EMERALD -> SAPPHIRE;
            case SAPPHIRE -> RUBY;
            case RUBY -> DIAMOND;
            case DIAMOND -> DIAMOND; // 최고 레벨은 그대로
        };
    }

    /**
     * 현재 레벨 내 진행률 계산 (0.0 ~ 1.0)
     *
     * @param currentXp 현재 XP
     * @return 진행률 (0.0 ~ 1.0)
     */
    public double getProgressInLevel(int currentXp) {
        AlgoLevel nextLevel = getNextLevel();
        if (nextLevel == this) {
            return 1.0; // 최고 레벨
        }

        int levelRange = nextLevel.requiredXp - this.requiredXp;
        int progressXp = currentXp - this.requiredXp;
        return Math.min(1.0, Math.max(0.0, (double) progressXp / levelRange));
    }
}
