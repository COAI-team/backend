package kr.or.kosa.backend.algorithm.controller;

import kr.or.kosa.backend.comment.dto.CommentCreateRequest;
import kr.or.kosa.backend.comment.dto.CommentResponse;
import kr.or.kosa.backend.comment.dto.CommentUpdateRequest;
import kr.or.kosa.backend.comment.service.CommentService;
import kr.or.kosa.backend.commons.pagination.CursorRequest;
import kr.or.kosa.backend.commons.pagination.CursorResponse;
import kr.or.kosa.backend.commons.response.ApiResponse;
import kr.or.kosa.backend.like.domain.ReferenceType;
import kr.or.kosa.backend.like.dto.LikeToggleResponse;
import kr.or.kosa.backend.like.service.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/algo/submissions")
@RequiredArgsConstructor
public class AlgorithmSocialController {

    private final LikeService likeService;
    private final CommentService commentService;

    @PostMapping("/{submissionId}/like")
    public ResponseEntity<ApiResponse<LikeToggleResponse>> toggleLike(
            @PathVariable Long submissionId,
            @RequestAttribute("userId") Long userId
    ) {
        LikeToggleResponse response = likeService.toggleLike(userId, ReferenceType.SUBMISSION, submissionId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{submissionId}/comments")
    public ResponseEntity<ApiResponse<CursorResponse<CommentResponse>>> getComments(
            @PathVariable Long submissionId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = "userId", required = false) Long userId
    ) {
        CursorRequest cursorRequest = new CursorRequest(cursor, size);

        CursorResponse<CommentResponse> response = commentService.getComments(
                submissionId,
                "SUBMISSION",
                cursorRequest,
                userId
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{submissionId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @PathVariable Long submissionId,
            @RequestBody CommentCreateRequest request,
            @RequestAttribute("userId") Long userId
    ) {
        // CommentCreateRequest에 이미 boardId, boardType이 있으면 덮어쓰기
        CommentCreateRequest updatedRequest = new CommentCreateRequest(
                submissionId,
                "SUBMISSION",
                request.parentCommentId(),
                request.content()
        );

        CommentResponse response = commentService.createComment(updatedRequest, userId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> updateComment(
            @PathVariable Long commentId,
            @RequestBody CommentUpdateRequest request,
            @RequestAttribute("userId") Long userId
    ) {
        commentService.updateComment(commentId, request, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable Long commentId,
            @RequestAttribute("userId") Long userId
    ) {
        commentService.deleteComment(commentId, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}