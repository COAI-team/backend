package kr.or.kosa.backend.algorithm.dto.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 데일리 미션 유형
 */
@Getter
@RequiredArgsConstructor
public enum MissionType {
    PROBLEM_GENERATE("AI 문제 생성하기"),
    PROBLEM_SOLVE("문제 풀기");

    private final String description;

    /**
     * UsageType으로 변환
     */
    public UsageType toUsageType() {
        return switch (this) {
            case PROBLEM_GENERATE -> UsageType.GENERATE;
            case PROBLEM_SOLVE -> UsageType.SOLVE;
        };
    }
}
