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

    // 태그와 좋아요 여부 포함한 새 인스턴스 생성
    public FreeboardDetailResponseDto withTags(List<String> tags) {
        return FreeboardDetailResponseDto.builder()
                .freeboardId(this.freeboardId)
                .userId(this.userId)
                .userNickname(this.userNickname)
                .freeboardTitle(this.freeboardTitle)
                .freeboardContent(this.freeboardContent)
                .freeboardClick(this.freeboardClick)
                .likeCount(this.likeCount)
                .commentCount(this.commentCount)
                .freeboardImage(this.freeboardImage)
                .freeboardRepresentImage(this.freeboardRepresentImage)
                .freeboardCreatedAt(this.freeboardCreatedAt)
                .tags(tags)
                .isLiked(this.isLiked)
                .build();
    }
}