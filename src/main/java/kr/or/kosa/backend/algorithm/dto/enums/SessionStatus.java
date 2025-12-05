package kr.or.kosa.backend.algorithm.dto.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 모니터링 세션 상태 Enum
 *
 * ACTIVE: 세션 진행 중
 * COMPLETED: 정상 완료 (사용자가 직접 제출)
 * TIMEOUT: 시간 초과로 자동 제출
 * TERMINATED: 강제 종료 (위반 과다 등)
 */
@Getter
@RequiredArgsConstructor
public enum SessionStatus {
    ACTIVE("진행 중"),
    COMPLETED("정상 완료"),
    TIMEOUT("시간 초과"),
    TERMINATED("강제 종료");

    private final String description;
}
