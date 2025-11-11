package kr.or.kosa.backend.freeboard.controller;

import kr.or.kosa.backend.freeboard.dto.Freeboard;
import kr.or.kosa.backend.freeboard.service.FreeboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/freeboard")
@RequiredArgsConstructor
public class FreeboardController {

    private final FreeboardService service;

    // 전체 조회
    @GetMapping
    public List<Freeboard> list() {
        return service.getAll();
    }

    // 단건 조회
    @GetMapping("/{id}")
    public Freeboard detail(@PathVariable Long id) {
        return service.getById(id);
    }

    // 등록
    @PostMapping
    public String create(@RequestBody Freeboard board) {
        service.create(board);
        return "등록 성공";
    }

    // 수정
    @PutMapping("/{id}")
    public String update(@PathVariable Long id, @RequestBody Freeboard board) {
        board.setFreeboardId(id);
        service.update(board);
        return "수정 성공";
    }

    // 삭제 (soft delete)
    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        service.delete(id);
        return "삭제 성공";
    }
}

