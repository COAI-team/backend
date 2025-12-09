package kr.or.kosa.backend.algorithm.dto.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 사용자 기능 사용 유형
 * Rate Limiting에서 사용
 */
@Getter
@RequiredArgsConstructor
public enum UsageType {
    GENERATE("AI 문제 생성"),
    SOLVE("문제 풀기");

    private final String description;
}
