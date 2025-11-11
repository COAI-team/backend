package kr.or.kosa.backend.freeboard.mapper;

import kr.or.kosa.backend.freeboard.dto.Freeboard;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface FreeboardMapper {
    List<Freeboard> selectAll();
    Freeboard selectById(Long id);
    void insert(Freeboard board);
    void update(Freeboard board);
    void delete(Long id);
}
