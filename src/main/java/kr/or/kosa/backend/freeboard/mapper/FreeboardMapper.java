package kr.or.kosa.backend.freeboard.mapper;

import kr.or.kosa.backend.freeboard.dto.FreeboardDetailResponseDto;
import kr.or.kosa.backend.freeboard.dto.FreeboardListResponseDto;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import kr.or.kosa.backend.freeboard.domain.Freeboard;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FreeboardMapper {

    // 특정 페이지의 게시글 목록
    List<FreeboardListResponseDto> selectPage(@Param("offset") int offset, @Param("size") int size);

    // 게시글 상세 조회
    FreeboardDetailResponseDto selectById(@Param("freeboardId") Long freeboardId);

    // 게시글 작성
    int insert(Freeboard board);

    // 게시글 수정
    int update(Freeboard board);

    // 게시글 삭제 (소프트 삭제)
    int delete(Long id);

    // 조회수 증가
    int increaseClick(Long id);

    // 총 게시글 수 (페이징용)
    int countAll();

    // 좋아요 개수 증가
    void incrementLikeCount(@Param("freeboardId") Long freeboardId);

    // 좋아요 개수 감소
    void decrementLikeCount(@Param("freeboardId") Long freeboardId);

    // 검색 조건에 맞는 게시글 수
    int countPosts(@Param("keyword") String keyword);

    // 검색/정렬 기능이 포함된 게시글 목록
    List<FreeboardListResponseDto> findPosts(
            @Param("offset") int offset,
            @Param("pageSize") int pageSize,
            @Param("sortField") String sortField,
            @Param("sortDirection") String sortDirection,
            @Param("keyword") String keyword
    );
}