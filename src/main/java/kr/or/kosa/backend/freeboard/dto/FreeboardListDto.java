package kr.or.kosa.backend.freeboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FreeboardListDto {
    private Long freeboardId;
    private Long userId;
    private String freeboardTitle;
    private String freeboardSummary;  // 미리보기용 요약
    private String freeboardRepresentImage;
    private Long freeboardClick;
    private LocalDateTime freeboardCreatedAt;
}
