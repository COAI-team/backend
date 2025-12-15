package kr.or.kosa.backend.codenose.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMistakeStatDTO {
    private Long statId;
    private Long userId;
    private String mistakeType;
    private int occurrenceCount;
    private int solvedCount;
    private Timestamp lastDetectedAt;
    private Timestamp lastReportGeneratedAt;
}
