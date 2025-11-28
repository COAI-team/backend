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
public class FocusSession {
    private String sessionId;
    private Long userId;
    private Long problemId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status; // ACTIVE, COMPLETED, TERMINATED
    private Integer violationCount;
}
