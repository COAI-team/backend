package kr.or.kosa.backend.tag.mapper;

import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import kr.or.kosa.backend.tag.dto.TagDTO;
import kr.or.kosa.backend.tag.domain.Tag;

@Mapper
public interface TagMapper {

    // 태그 전체 조회
    List<Tag> findAllTags();

    // 특정 게시글의 태그 조회 (자유게시판)
    List<Tag> findTagsByFreeboardId(Long freeboardId);

    // 특정 게시글의 태그 조회 (코드게시판)
    List<Tag> findTagsByCodeboardId(Long codeboardId);

    // 태그 등록 (새 태그면 TAG 테이블에 insert)
    void insertTag(Tag tag);

    // 매핑 등록 (자유게시판)
    void insertFreeboardTag(TagDTO dto);

    // 매핑 등록 (코드게시판)
    void insertCodeboardTag(TagDTO dto);

    // 매핑 삭제 (게시글 삭제 시)
    void deleteByFreeboardId(Long freeboardId);
    void deleteByCodeboardId(Long codeboardId);
}
