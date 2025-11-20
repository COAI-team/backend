package kr.or.kosa.backend.freeComment.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import kr.or.kosa.backend.freeComment.domain.FreeComment;
import kr.or.kosa.backend.freeComment.service.FreeCommentService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/freecomment")
public class FreeCommentController {

    private final FreeCommentService freeCommentService;

    // 댓글 목록 조회 (게시글 ID 기준)
    @GetMapping("/{boardId}")
    public List<FreeComment> getComments(@PathVariable Long boardId) {
        return freeCommentService.getCommentsByBoardId(boardId);
    }

    // 댓글 등록
    @PostMapping
    public void writeComment(@RequestBody FreeComment comment) {
        freeCommentService.writeComment(comment);
    }

    // 댓글 수정
    @PutMapping
    public void editComment(@RequestBody FreeComment comment) {
        freeCommentService.editComment(comment);
    }

    // 댓글 삭제
    @DeleteMapping("/{commentId}")
    public void deleteComment(@PathVariable Long commentId) {
        freeCommentService.deleteComment(commentId);
    }
}
