package kr.or.kosa.backend.codeboard.mapper;

import kr.or.kosa.backend.codeboard.domain.Codeboard;
import kr.or.kosa.backend.codeboard.dto.CodeboardDetailResponseDto;
import kr.or.kosa.backend.codeboard.dto.CodeboardListResponseDto;
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

    int countAll();

    // 좋아요 개수 증가
    void incrementLikeCount(@Param("freeboardId") Long freeboardId);

    // 좋아요 개수 감소
    void decrementLikeCount(@Param("freeboardId") Long freeboardId);

    int countPosts(@Param("keyword") String keyword);

    List<CodeboardListResponseDto> findPosts(
            @Param("offset") int offset,
            @Param("pageSize") int pageSize,
            @Param("sortField") String sortField,
            @Param("sortDirection") String sortDirection,
            @Param("keyword") String keyword
    );
}