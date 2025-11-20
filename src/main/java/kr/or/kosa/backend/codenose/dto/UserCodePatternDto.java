package kr.or.kosa.backend.codenose.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserCodePatternDto {
    private String patternId;
    private Long userId;
    private String patternType;
    private int frequency;
    private Timestamp lastDetected;
    private String improvementStatus;
}
