package kr.or.kosa.backend.freeComment.mapper;

import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import kr.or.kosa.backend.freeComment.domain.FreeComment;

@Mapper
public interface FreeCommentMapper {

    // 특정 게시글의 댓글 전체 조회
    List<FreeComment> findByBoardId(Long freeboardId);

    // 댓글 등록
    void insertComment(FreeComment comment);

    // 댓글 수정
    void updateComment(FreeComment comment);

    // 댓글 삭제 (소프트 삭제)
    void deleteComment(Long freeCommentId);
}
