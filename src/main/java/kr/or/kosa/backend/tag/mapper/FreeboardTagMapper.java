package kr.or.kosa.backend.tag.mapper;

import kr.or.kosa.backend.tag.domain.FreeboardTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FreeboardTagMapper {

    List<FreeboardTag> findByFreeboardId(@Param("freeboardId") Long freeboardId);

    List<FreeboardTag> findByFreeboardIdIn(@Param("freeboardIds") List<Long> freeboardIds);

    List<FreeboardTag> findByTagId(@Param("tagId") Long tagId);

    int insert(FreeboardTag freeboardTag);

    int deleteByFreeboardId(@Param("freeboardId") Long freeboardId);

    Long countByTagId(@Param("tagId") Long tagId);
}
