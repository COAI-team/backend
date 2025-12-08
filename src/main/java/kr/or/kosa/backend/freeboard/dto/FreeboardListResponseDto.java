package kr.or.kosa.backend.freeboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FreeboardListResponseDto {
    private Long freeboardId;
    private Long userId;
    private String userNickname;
    private String freeboardTitle;
    private String freeboardSummary;
    private String freeboardRepresentImage;
    private Long freeboardClick;
    private Integer likeCount;
    private Integer commentCount;
    private List<String> tags;
    private LocalDateTime freeboardCreatedAt;
}