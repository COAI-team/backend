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
public class TutorServerMessage {

    private String type;       // "HINT", "INFO", "ERROR", etc.
    private String content;    // Message to display to the user
    private Long problemId;
    private String userId;
    private String triggerType;
}
