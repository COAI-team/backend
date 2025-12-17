package kr.or.kosa.backend.algorithm.dto.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 문제 난이도 Enum
 * 데이터베이스에는 문자열로 저장됨
 *
 * 변경사항 (2025-12-17): XP 기반 레벨 시스템 도입
 * - baseXp 필드 추가: 문제 풀이 시 획득하는 기본 경험치
 * - 첫 정답 보너스(+50%)는 서비스 로직에서 처리
 */
@Getter
@RequiredArgsConstructor
public enum ProblemDifficulty {

    BRONZE("BRONZE", "브론즈", 1, "#cd7f32", 10),
    SILVER("SILVER", "실버", 2, "#c0c0c0", 25),
    GOLD("GOLD", "골드", 3, "#ffd700", 50),
    PLATINUM("PLATINUM", "플래티넘", 4, "#e5e4e2", 100);

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
     * 기본 경험치 (XP)
     * - 문제 풀이 시 획득하는 기본 XP
     * - 첫 정답 보너스(+50%)는 서비스 로직에서 추가 계산
     */
    private final int baseXp;

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

    /**
     * 첫 정답 보너스를 포함한 총 XP 계산
     * 첫 정답 보너스: 기본 XP의 50%
     *
     * @return 기본 XP + 첫 정답 보너스 (반올림)
     */
    public int getFirstSolveXp() {
        return (int) Math.round(baseXp * 1.5);
    }

    /**
     * 스트릭 보너스를 적용한 XP 계산
     *
     * @param currentStreak 현재 연속 풀이 일수
     * @param isFirstSolve 첫 정답 여부
     * @return 보너스가 적용된 총 XP
     */
    public int calculateXpWithBonus(int currentStreak, boolean isFirstSolve) {
        int xp = isFirstSolve ? getFirstSolveXp() : baseXp;

        // 스트릭 보너스 적용 (3일: 10%, 7일: 20%, 14일: 30%, 30일: 50%)
        double streakMultiplier = getStreakMultiplier(currentStreak);
        return (int) Math.round(xp * streakMultiplier);
    }

    /**
     * 스트릭에 따른 보너스 배율 반환
     */
    private double getStreakMultiplier(int streak) {
        if (streak >= 30) return 1.5;
        if (streak >= 14) return 1.3;
        if (streak >= 7) return 1.2;
        if (streak >= 3) return 1.1;
        return 1.0;
    }
}
