package kr.or.kosa.backend.codeboard.mapper;

import kr.or.kosa.backend.codeboard.domain.Codeboard;
import kr.or.kosa.backend.codeboard.dto.CodeboardDetailResponseDto;
import kr.or.kosa.backend.codeboard.dto.CodeboardListResponseDto;
import kr.or.kosa.backend.commons.pagination.PageRequest;
import kr.or.kosa.backend.commons.pagination.SearchCondition;
import kr.or.kosa.backend.commons.pagination.SortCondition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CodeboardMapper {

    // 게시글 목록 조회 (페이징 + 검색 + 정렬)
    List<CodeboardListResponseDto> findPosts(
            @Param("page") PageRequest pageRequest,
            @Param("search") SearchCondition searchCondition,
            @Param("sort") SortCondition sortCondition
    );

    //전체 개수 조회 (검색 조건 적용)
    long countPosts(@Param("search") SearchCondition searchCondition);

    // 게시글 상세 조회
    CodeboardDetailResponseDto selectById(@Param("codeboardId") Long codeboardId, @Param("userId") Long userId);

    // 게시글 작성
    int insert(Codeboard codeboard);

    // 게시글 수정
    int update(Codeboard codeboard);

    // 게시글 소프트 삭제
    int delete(Long codeboardId);

    // 조회수 증가
    void increaseClick(Long codeboardId);
}