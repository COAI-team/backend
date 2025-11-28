package kr.or.kosa.backend.comment.dto;

import kr.or.kosa.backend.comment.domain.Comment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentResponse {

    private Long commentId;
    private Long boardId;
    private String boardType;
    private Long parentCommentId;
    private Long userId;
    private String userNickname;
    private String content;
    private Integer likeCount;
    private Boolean isLiked;
    private Boolean isAuthor;
    private Boolean isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CommentResponse from(Comment comment) {
        return CommentResponse.builder()
                .commentId(comment.getCommentId())
                .boardId(comment.getBoardId())
                .boardType(comment.getBoardType())
                .parentCommentId(comment.getParentCommentId())
                .userId(comment.getUserId())
                .content(comment.getContent())
                .likeCount(comment.getLikeCount())
                .isDeleted(comment.getIsDeleted())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}
