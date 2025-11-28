package kr.or.kosa.backend.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 댓글 수정 요청
public record CommentUpdateRequest(
        @NotBlank(message = "내용은 필수입니다")
        @Size(max = 1000, message = "댓글은 3000자를 초과할 수 없습니다")
        String content
) {}

