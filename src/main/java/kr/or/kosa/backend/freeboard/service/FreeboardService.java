package kr.or.kosa.backend.freeboard.service;

import kr.or.kosa.backend.freeboard.dto.Freeboard;
import kr.or.kosa.backend.freeboard.mapper.FreeboardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FreeboardService {

    private final FreeboardMapper mapper;

    public List<Freeboard> getAll() {
        return mapper.selectAll();
    }

    public Freeboard getById(Long id) {
        return mapper.selectById(id);
    }

    public void create(Freeboard board) {
        mapper.insert(board);
    }

    public void update(Freeboard board) {
        mapper.update(board);
    }

    public void delete(Long id) {
        mapper.delete(id);
    }
}
