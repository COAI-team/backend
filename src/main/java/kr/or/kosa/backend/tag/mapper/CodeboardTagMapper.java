package kr.or.kosa.backend.tag.mapper;

import kr.or.kosa.backend.tag.domain.CodeboardTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CodeboardTagMapper {

    List<CodeboardTag> findByCodeboardId(@Param("codeboardId") Long codeboardId);

    List<CodeboardTag> findByCodeboardIdIn(@Param("codeboardIds") List<Long> codeboardIds);

    List<CodeboardTag> findByTagId(@Param("tagId") Long tagId);

    int insert(CodeboardTag codeboardTag);

    int deleteByCodeboardId(@Param("codeboardId") Long codeboardId);

    Long countByTagId(@Param("tagId") Long tagId);
}
