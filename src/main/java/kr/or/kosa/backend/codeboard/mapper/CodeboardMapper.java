package kr.or.kosa.backend.codeboard.mapper;

import kr.or.kosa.backend.codeboard.domain.Codeboard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CodeboardMapper {

    int insert(Codeboard codeboard);

    Codeboard selectById(@Param("id") Long id);

    int update(Codeboard codeboard);

    int delete(@Param("id") Long id);

    List<Codeboard> selectPage(@Param("offset") int offset, @Param("size") int size);

    int countAll();

    void increaseClick(@Param("id") Long id);
}
