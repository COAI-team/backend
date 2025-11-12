package kr.or.kosa.backend.freeComment.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FreeComment {
    private Long freeCommentId;        // 댓글 고유번호
    private Long freeboardId;          // 게시글 FK
    private Long freeCommentParentId;  // 부모 댓글 ID (대댓글)
    private Long userId;               // 작성자 ID
    private String freeCommentContent; // 댓글 내용
    private LocalDateTime freeCommentCreatedAt; // 작성일시
    private String freeCommentDeletedYn; // 삭제 여부 (Y/N)
}
