package kr.or.kosa.backend.freeboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import kr.or.kosa.backend.freeboard.domain.Freeboard;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FreeboardMapper {

    // 특정 페이지의 게시글 목록
    List<Freeboard> selectPage(@Param("offset") int offset, @Param("size") int size);

    // 게시글 상세 조회
    Freeboard selectById(Long id);

    // 게시글 작성
    void insert(Freeboard board);

    // 게시글 수정
    void update(Freeboard board);

    // 게시글 삭제 (소프트 삭제)
    void delete(Long id);

    // 조회수 증가
    void increaseClick(Long id);

    // 총 게시글 수 (페이징용)
    int countAll();
}
