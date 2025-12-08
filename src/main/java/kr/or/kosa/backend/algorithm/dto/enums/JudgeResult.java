package kr.or.kosa.backend.algorithm.dto.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 채점 결과 Enum
 */
@Getter
@RequiredArgsConstructor
public enum JudgeResult {
    AC("Accepted"),
    WA("Wrong Answer"),
    TLE("Time Limit Exceeded"),
    MLE("Memory Limit Exceeded"),
    RE("Runtime Error"),
    CE("Compile Error"),
    PENDING("Pending");

    private final String description;
}
