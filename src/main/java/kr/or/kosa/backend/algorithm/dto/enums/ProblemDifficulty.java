package kr.or.kosa.backend.algorithm.dto.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 문제 난이도 Enum
 * 데이터베이스에는 문자열로 저장됨
 */
@Getter
@RequiredArgsConstructor
public enum ProblemDifficulty {

    BRONZE("BRONZE", "브론즈", 1, "#cd7f32"),
    SILVER("SILVER", "실버", 2, "#c0c0c0"),
    GOLD("GOLD", "골드", 3, "#ffd700"),
    PLATINUM("PLATINUM", "플래티넘", 4, "#e5e4e2");

    /**
     * 데이터베이스 저장값
     */
    private final String dbValue;

    /**
     * 한국어 표시명
     */
    private final String displayName;

    /**
     * 난이도 레벨 (숫자)
     */
    private final int level;

    /**
     * UI 표시용 색상 코드
     */
    private final String color;

    /**
     * 데이터베이스 값으로 Enum 찾기
     */
    public static ProblemDifficulty fromDbValue(String dbValue) {
        if (dbValue == null) {
            return null;
        }

        for (ProblemDifficulty difficulty : values()) {
            if (difficulty.dbValue.equals(dbValue)) {
                return difficulty;
            }
        }

        throw new IllegalArgumentException("Unknown difficulty: " + dbValue);
    }

    /**
     * 레벨로 Enum 찾기
     */
    public static ProblemDifficulty fromLevel(int level) {
        for (ProblemDifficulty difficulty : values()) {
            if (difficulty.level == level) {
                return difficulty;
            }
        }

        throw new IllegalArgumentException("Unknown level: " + level);
    }

    /**
     * 다음 난이도 반환
     */
    public ProblemDifficulty getNext() {
        return switch (this) {
            case BRONZE -> SILVER;
            case SILVER -> GOLD;
            case GOLD -> PLATINUM;
            case PLATINUM -> PLATINUM; // 최고 난이도는 그대로
        };
    }

    /**
     * 이전 난이도 반환
     */
    public ProblemDifficulty getPrevious() {
        return switch (this) {
            case BRONZE -> BRONZE; // 최저 난이도는 그대로
            case SILVER -> BRONZE;
            case GOLD -> SILVER;
            case PLATINUM -> GOLD;
        };
    }

    /**
     * 해당 난이도보다 높은지 확인
     */
    public boolean isHigherThan(ProblemDifficulty other) {
        return this.level > other.level;
    }

    /**
     * 해당 난이도보다 낮은지 확인
     */
    public boolean isLowerThan(ProblemDifficulty other) {
        return this.level < other.level;
    }
}
