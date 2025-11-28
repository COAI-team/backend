package kr.or.kosa.backend.comment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

// 댓글과 대댓글을 포함한 응답
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentWithRepliesResponse {

    private Long commentId;
    private Long boardId;
    private String boardType;
    private Long userId;
    private String userNickname;
    private String content;
    private Integer likeCount;
    private Boolean isLiked;
    private Boolean isAuthor;
    private Boolean isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<CommentResponse> replies;
}
