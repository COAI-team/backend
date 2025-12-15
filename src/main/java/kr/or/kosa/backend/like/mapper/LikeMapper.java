package kr.or.kosa.backend.like.mapper;

import kr.or.kosa.backend.like.domain.Like;
import kr.or.kosa.backend.like.domain.ReferenceType;
import kr.or.kosa.backend.like.dto.LikeUserDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LikeMapper {

    // 좋아요 추가
    int insertLike(Like like);

    // 좋아요 삭제
    int deleteLike(@Param("userId") Long userId,
                   @Param("referenceType") ReferenceType referenceType,
                   @Param("referenceId") Long referenceId);

    // 좋아요 존재 여부 확인
    Like selectLike(@Param("userId") Long userId,
                    @Param("referenceType") ReferenceType referenceType,
                    @Param("referenceId") Long referenceId);

    // 사용자가 좋아요 누른 ID 목록 조회
    List<Long> selectLikedReferenceIds(@Param("userId") Long userId,
                                       @Param("referenceType") ReferenceType referenceType,
                                       @Param("referenceIds") List<Long> referenceIds);

    // 참조 대상의 모든 좋아요 삭제
    int deleteByReference(@Param("referenceType") ReferenceType referenceType,
                          @Param("referenceId") Long referenceId);

    // 특정 게시글의 좋아요 누른 사용자 목록 조회
    List<LikeUserDto> selectLikeUsers(@Param("referenceType") ReferenceType referenceType,
                                      @Param("referenceId") Long referenceId,
                                      @Param("limit") int limit);
}