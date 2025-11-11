package kr.or.kosa.backend.freeboard.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Freeboard {
    private Long freeboardId;
    private Long userId;
    private String freeboardTitle;
    private String freeboardContent;
    private Long freeboardClick;
    private String freeboardImage;
    private String freeboardRepresentImage;
    private LocalDateTime freeboardCreatedAt;
    private String freeboardDeletedYn;
}
