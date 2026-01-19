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
public class FreeboardDetailResponseDto {
    private Long freeboardId;
    private Long userId;
    private String userNickname;
    private String userImage;
    private String freeboardTitle;
    private String freeboardContent;
    private Long freeboardClick;
    private Integer likeCount;
    private Integer commentCount;
    private String freeboardImage;
    private String freeboardRepresentImage;
    private LocalDateTime freeboardCreatedAt;
    private List<String> tags;
    private Boolean isLiked;

}