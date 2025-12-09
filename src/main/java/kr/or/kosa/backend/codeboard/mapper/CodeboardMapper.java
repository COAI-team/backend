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

    int insert(Codeboard codeboard);

    int update(Codeboard codeboard);

    int delete(Long codeboardId);

    CodeboardDetailResponseDto selectById(Long codeboardId);

    void increaseClick(Long codeboardId);

    long countPosts(@Param("search") SearchCondition searchCondition);

    List<CodeboardListResponseDto> findPosts(
            @Param("page") PageRequest pageRequest,
            @Param("search") SearchCondition searchCondition,
            @Param("sort") SortCondition sortCondition
    );
}