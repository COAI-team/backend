package kr.or.kosa.backend.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CommentCreateRequest(
        @NotNull(message = "게시글 ID는 필수입니다")
        Long boardId,

        @NotBlank(message = "게시판 타입은 필수입니다")
        String boardType,

        Long parentCommentId,

        @NotBlank(message = "내용은 필수입니다")
        @Size(max = 1000, message = "댓글은 3000자를 초과할 수 없습니다")
        String content
) {}
