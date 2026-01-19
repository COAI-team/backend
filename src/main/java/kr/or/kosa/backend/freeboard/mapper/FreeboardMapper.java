package kr.or.kosa.backend.freeboard.mapper;

import kr.or.kosa.backend.commons.pagination.PageRequest;
import kr.or.kosa.backend.commons.pagination.SearchCondition;
import kr.or.kosa.backend.commons.pagination.SortCondition;
import kr.or.kosa.backend.freeboard.domain.Freeboard;
import kr.or.kosa.backend.freeboard.dto.FreeboardDetailResponseDto;
import kr.or.kosa.backend.freeboard.dto.FreeboardListResponseDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FreeboardMapper {

    // 게시글 목록 조회 (페이징 + 검색 + 정렬)
    List<FreeboardListResponseDto> findPosts(
            @Param("page") PageRequest pageRequest,
            @Param("search") SearchCondition searchCondition,
            @Param("sort") SortCondition sortCondition
    );

    // 전체 개수 조회 (검색 조건 적용)
    long countPosts(@Param("search") SearchCondition searchCondition);

    // 게시글 상세 조회
    FreeboardDetailResponseDto selectById(@Param("freeboardId") Long freeboardId);

    // 게시글 작성
    int insert(Freeboard freeboard);

    // 게시글 수정
    int update(Freeboard freeboard);

    // 게시글 삭제 (소프트 삭제)
    int delete(@Param("freeboardId") Long freeboardId);

    // 조회수 증가
    void increaseClick(@Param("freeboardId") Long freeboardId);
}