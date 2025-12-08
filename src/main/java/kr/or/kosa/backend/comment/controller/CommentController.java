package kr.or.kosa.backend.comment.controller;

import jakarta.validation.Valid;
import kr.or.kosa.backend.comment.dto.CommentCreateRequest;
import kr.or.kosa.backend.comment.dto.CommentResponse;
import kr.or.kosa.backend.comment.dto.CommentUpdateRequest;
import kr.or.kosa.backend.comment.dto.CommentWithRepliesResponse;
import kr.or.kosa.backend.comment.service.CommentService;
import kr.or.kosa.backend.commons.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Log4j2
@RestController
@RequestMapping("/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @Valid @RequestBody CommentCreateRequest request,
            @RequestAttribute(value = "userId", required = false) Long userId
    ) {
        Long actualUserId = (userId != null) ? userId : 1L;
        CommentResponse response = commentService.createComment(request, actualUserId);

        Map<String, Object> result = new HashMap<>();
        result.put("commentId", response.getCommentId());
        result.put("comment", response);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CommentWithRepliesResponse>>> list(
            @RequestParam String boardType,
            @RequestParam Long boardId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = "userId", required = false) Long userId
    ) {
        Long currentUserId = (userId != null) ? userId : 1L;

        List<CommentWithRepliesResponse> comments = commentService.getCommentsByBoard(
                boardId,
                boardType,
                cursor,
                size,
                currentUserId
        );

        return ResponseEntity.ok(ApiResponse.success(comments));
    }

    @PutMapping("/{commentId}")
    public ResponseEntity<Void> update(
            @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequest request,
            @RequestAttribute(value = "userId", required = false) Long userId
    ) {
        log.info("=== update 컨트롤러 시작 ===");
        log.info("commentId: {}", commentId);
        log.info("request: {}", request);

        Long actualUserId = (userId != null) ? userId : 1;
        commentService.updateComment(commentId, request, actualUserId);

        log.info("=== update 컨트롤러 완료 ===");
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long commentId,
            @RequestAttribute(value = "userId", required = false) Long userId
    ) {
        Long actualUserId = (userId != null) ? userId : 1;
        commentService.deleteComment(commentId, actualUserId);
        return ResponseEntity.ok().build();
    }
}