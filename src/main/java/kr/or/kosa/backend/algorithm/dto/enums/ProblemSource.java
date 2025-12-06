package kr.or.kosa.backend.algorithm.dto.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 문제 생성 출처 Enum
 * 데이터베이스에는 문자열로 저장됨
 */
@Getter
@RequiredArgsConstructor
public enum ProblemSource {

    AI_GENERATED("AI_GENERATED", "AI 생성", "🤖", "AI가 자동으로 생성한 문제"),
    BOJ("BOJ", "백준", "🏆", "백준 온라인 저지에서 가져온 문제"),
    PROGRAMMERS("PROGRAMMERS", "프로그래머스", "💻", "프로그래머스에서 가져온 문제"),
    CUSTOM("CUSTOM", "커스텀", "✏️", "사용자가 직접 작성한 문제");

    /**
     * 데이터베이스 저장값
     */
    private final String dbValue;

    /**
     * 한국어 표시명
     */
    private final String displayName;

    /**
     * UI 아이콘 (이모지)
     */
    private final String icon;

    /**
     * 설명
     */
    private final String description;

    /**
     * 데이터베이스 값으로 Enum 찾기
     */
    public static ProblemSource fromDbValue(String dbValue) {
        if (dbValue == null) {
            return null;
        }

        for (ProblemSource source : values()) {
            if (source.dbValue.equals(dbValue)) {
                return source;
            }
        }

        throw new IllegalArgumentException("Unknown problem source: " + dbValue);
    }

    /**
     * AI 생성 문제인지 확인
     */
    public boolean isAIGenerated() {
        return this == AI_GENERATED;
    }

    /**
     * 외부 플랫폼 문제인지 확인
     */
    public boolean isExternalSource() {
        return this == BOJ || this == PROGRAMMERS;
    }

    /**
     * 사용자 생성 문제인지 확인
     */
    public boolean isUserCreated() {
        return this == CUSTOM;
    }

    /**
     * 아이콘과 이름을 결합한 표시명
     */
    public String getDisplayNameWithIcon() {
        return icon + " " + displayName;
    }

    /**
     * CSS 클래스명 반환 (UI 스타일링용)
     */
    public String getCssClass() {
        return switch (this) {
            case AI_GENERATED -> "source-ai";
            case BOJ -> "source-boj";
            case PROGRAMMERS -> "source-programmers";
            case CUSTOM -> "source-custom";
        };
    }

    /**
     * 배경 색상 반환 (UI 배지용)
     */
    public String getBackgroundColor() {
        return switch (this) {
            case AI_GENERATED -> "#e3f2fd";  // 연한 파란색
            case BOJ -> "#fff3e0";           // 연한 주황색
            case PROGRAMMERS -> "#f3e5f5";   // 연한 보라색
            case CUSTOM -> "#e8f5e9";        // 연한 초록색
        };
    }

    /**
     * 텍스트 색상 반환 (UI 배지용)
     */
    public String getTextColor() {
        return switch (this) {
            case AI_GENERATED -> "#1976d2";  // 진한 파란색
            case BOJ -> "#f57c00";           // 진한 주황색
            case PROGRAMMERS -> "#7b1fa2";   // 진한 보라색
            case CUSTOM -> "#2e7d32";        // 진한 초록색
        };
    }
}
