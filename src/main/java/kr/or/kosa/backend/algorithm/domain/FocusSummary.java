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
public class FocusSummary {
    private Long summaryId;
    private String sessionId;
    private Long userId;
    private Integer totalEvents;
    private Double focusInPercentage;
    private Integer totalViolations;
    private Integer criticalViolations;
    private Double finalFocusScore;
    private String focusGrade;
    private LocalDateTime processedAt;
}
