package kr.or.kosa.backend.freeComment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import kr.or.kosa.backend.freeComment.domain.FreeComment;
import kr.or.kosa.backend.freeComment.mapper.FreeCommentMapper;

@Service
@RequiredArgsConstructor
public class FreeCommentService {

    private final FreeCommentMapper freeCommentMapper;

    // 게시글의 모든 댓글 조회
    public List<FreeComment> getCommentsByBoardId(Long boardId) {
        return freeCommentMapper.findByBoardId(boardId);
    }

    // 댓글 작성
    public void writeComment(FreeComment comment) {
        freeCommentMapper.insertComment(comment);
    }

    // 댓글 수정
    public void editComment(FreeComment comment) {
        freeCommentMapper.updateComment(comment);
    }

    // 댓글 삭제 (Y로 표시)
    public void deleteComment(Long commentId) {
        freeCommentMapper.deleteComment(commentId);
    }
}
