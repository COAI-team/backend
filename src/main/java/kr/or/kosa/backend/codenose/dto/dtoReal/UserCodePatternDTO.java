package kr.or.kosa.backend.codenose.dto.dtoReal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

/**
 * 사용자 코드 패턴 DTO
 * USER_CODE_PATTERNS 테이블 매핑
 *
 * 사용자의 반복되는 코드 패턴(코드 스멜)을 추적
 * - 특정 패턴의 발생 빈도, 마지막 감지 시간, 개선 상태 등을 관리
 * - USER 테이블과 N:1 관계 (한 사용자가 여러 코드 패턴 보유)
 * - CODE_ANALYSIS_HISTORY와 간접적 연관 (분석 결과에서 패턴 추출)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserCodePatternDTO {
    private String patternId;               // PK: 패턴 ID (UUID)
    private Long userId;                    // FK: 사용자 ID -> USER.user_id
    private String patternType;             // 패턴 타입 (Long Method, Magic Number 등)
    private int frequency;                  // 해당 패턴 발생 빈도
    private Timestamp lastDetected;         // 마지막으로 감지된 시간
    private String improvementStatus;       // 개선 상태 (Detected, In Progress, Resolved 등)
}
