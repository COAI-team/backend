package kr.or.kosa.backend.comment.mapper;

import kr.or.kosa.backend.comment.domain.Comment;
import kr.or.kosa.backend.comment.dto.CommentResponse;
import kr.or.kosa.backend.commons.pagination.CursorRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CommentMapper {

    // 댓글 생성
    Long insertComment(Comment comment);

    // 댓글 조회 (단건)
    Comment selectCommentById(@Param("commentId") Long commentId);

    // 댓글 + 답글 조회 (커서 기반 무한 스크롤)
    List<CommentResponse> selectCommentsWithReplies(
            @Param("boardId") Long boardId,
            @Param("boardType") String boardType,
            @Param("cursor") CursorRequest cursor,
            @Param("userId") Long userId
    );

    // 댓글 수정
    Long updateComment(Comment comment);

    // 댓글 소프트 삭제
    Long deleteComment(@Param("commentId") Long commentId);

    // 특정 사용자의 댓글인지 확인
    boolean existsByCommentIdAndUserId(
            @Param("commentId") Long commentId,
            @Param("userId") Long userId
    );
}