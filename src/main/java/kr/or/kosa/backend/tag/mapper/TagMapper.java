package kr.or.kosa.backend.tag.mapper;

import kr.or.kosa.backend.tag.domain.Tag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface TagMapper {

    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    Optional<Tag> findByTagName(@Param("tagName") String tagName);

    // 동시성을 위해 insert를 upsert로
    void upsertTag(Tag tag);

    List<Tag> findByTagNameStartingWith(@Param("keyword") String keyword);

    String findMostUsedDisplayName(@Param("tagId") Long tagId);

    Long countByTagId(@Param("tagId") Long tagId);

    // 사용되지 않는 태그 스케줄러가 삭제
    int deleteUnusedTags();
}
