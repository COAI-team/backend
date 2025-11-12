package kr.or.kosa.backend.freeboardLike.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Optional;

@Mapper
public interface FreeboardLikeMapper {

    // 특정 게시글의 좋아요 수
    int countByBoardId(Long freeboardId);

    // 사용자가 해당 글에 좋아요 눌렀는지 확인
    Optional<Integer> exists(@Param("freeboardId") Long freeboardId, @Param("userId") Long userId);

    // 좋아요 추가
    void insertLike(@Param("freeboardId") Long freeboardId, @Param("userId") Long userId);

    // 좋아요 취소
    void deleteLike(@Param("freeboardId") Long freeboardId, @Param("userId") Long userId);
}
