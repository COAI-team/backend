package kr.or.kosa.backend.codenose.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

/**
 * 사용자 코드 패턴 DTO (UserCodePatternDTO)
 * 
 * 역할:
 * `USER_CODE_PATTERNS` 테이블과 매핑되어 사용자의 코딩 습관 데이터를 관리합니다.
 * 반복적으로 발생하는 코드 스멜이나 특정 코딩 스타일을 추적하여 개인화된 피드백을 제공하는 데 사용됩니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserCodePatternDTO {
    private String patternId; // PK: 패턴 ID (UUID)
    private Long userId; // FK: 사용자 ID
    private String patternType; // 감지된 패턴 유형 (예: "Complex Method", "God Class")
    private int frequency; // 해당 패턴이 감지된 누적 횟수
    private Timestamp lastDetected; // 마지막으로 감지된 시각
    private String improvementStatus; // 개선 상태 상태값 (예: "Detected", "Improving", "Resolved")
}
