package kr.or.kosa.backend.freeboard.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PopularFreeboardResponseDto {
    private Long freeboardId;
    private Long userId;
    private String userNickname;
    private String userImage;
    private String freeboardTitle;
    private String freeboardPlainText;
    private Long freeboardClick;
    private String freeboardRepresentImage;
    private LocalDateTime freeboardCreatedAt;
    private Integer likeCount;
    private Integer commentCount;
    private Integer popularityScore;
    private Integer ranking;
    private List<String> tags;
}