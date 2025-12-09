package kr.or.kosa.backend.comment.controller;

import jakarta.validation.Valid;
import kr.or.kosa.backend.comment.dto.CommentCreateRequest;
import kr.or.kosa.backend.comment.dto.CommentResponse;
import kr.or.kosa.backend.comment.dto.CommentUpdateRequest;
import kr.or.kosa.backend.comment.service.CommentService;
import kr.or.kosa.backend.commons.pagination.CursorRequest;
import kr.or.kosa.backend.commons.pagination.CursorResponse;
import kr.or.kosa.backend.commons.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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
            @RequestAttribute("userId") Long userId
    ) {
        CommentResponse response = commentService.createComment(request, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("commentId", response.getCommentId());
        result.put("comment", response);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping
    public ResponseEntity<CursorResponse<CommentResponse>> getComments(
            @RequestParam Long boardId,
            @RequestParam String boardType,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = "userId", required = false) Long userId  // 비로그인 허용
    ) {
        CursorRequest cursorRequest = new CursorRequest(cursor, size);

        CursorResponse<CommentResponse> response =
                commentService.getComments(boardId, boardType, cursorRequest, userId);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{commentId}")
    public ResponseEntity<Void> update(
            @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequest request,
            @RequestAttribute("userId") Long userId
    ) {
        commentService.updateComment(commentId, request, userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long commentId,
            @RequestAttribute("userId") Long userId
    ) {
        commentService.deleteComment(commentId, userId);
        return ResponseEntity.ok().build();
    }
}