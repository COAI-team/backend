package kr.or.kosa.backend.algorithm.dto.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 문제 풀이 모드 Enum
 *
 * BASIC: 기본 모드 - 타이머/모니터링 없이 자유롭게 풀이
 * FOCUS: 집중 모드 - 제한 시간 + 모니터링 활성화
 */
@Getter
@RequiredArgsConstructor
public enum SolveMode {
    BASIC("기본 모드"),
    FOCUS("집중 모드");

    private final String description;
}
