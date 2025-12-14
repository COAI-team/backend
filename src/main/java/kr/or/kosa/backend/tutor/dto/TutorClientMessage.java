package kr.or.kosa.backend.tutor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TutorClientMessage {

    private Long problemId;
    private String userId;
    private String language;    // "JAVA", "PYTHON", etc.
    private String code;        // Full code snapshot from the client
    private String triggerType; // "AUTO" or "USER"
    private String message;     // Optional user question when triggerType is "USER"

    // Judge meta info
    private String judgeResult;   // "AC", "WA", "TLE", etc.
    private Integer passedCount;  // passed test case count
    private Integer totalCount;   // total test case count
}
