package kr.or.kosa.backend.algorithm.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ViolationLog {
    private Long violationId;
    private String sessionId;
    private Long userId;
    private String violationType; // TAB_SWITCH, SCREEN_EXIT, FACE_MISSING, SUSPICIOUS_PATTERN
    private String severity; // HIGH, CRITICAL
    private Integer occurrenceCount;
    private String autoAction; // WARNING, TIME_REDUCTION, SESSION_TERMINATE
    private Double penaltyScore;
    private LocalDateTime detectedAt;
    private Integer sessionTimeOffsetSeconds;
}
