package kr.or.kosa.backend.comment.mapper;

import kr.or.kosa.backend.comment.domain.Comment;
import kr.or.kosa.backend.comment.dto.CommentResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CommentMapper {

    // 댓글 생성
    Long insertComment(Comment comment);

    // 댓글 조회
    Comment selectCommentById(@Param("commentId") Long commentId);

    // 게시글의 댓글 목록 조회 (커서 기반 무한 스크롤)
    List<CommentResponse> selectCommentsByBoard(
            @Param("boardType") String boardType,
            @Param("boardId") Long boardId,
            @Param("cursor") Long cursor,
            @Param("size") int size
    );

    // 부모 댓글 ID 목록으로 대댓글 조회
    List<CommentResponse> selectRepliesByParentIds(@Param("parentCommentIds") List<Long> parentCommentIds);

    // 댓글 수정
    Long updateComment(Comment comment);

    // 댓글 소프트 삭제
    Long deleteComment(@Param("commentId") Long commentId);

    // 특정 사용자의 댓글인지 확인
    boolean existsByCommentIdAndUserId(@Param("commentId") Long commentId,
                                       @Param("userId") Long userId);
}