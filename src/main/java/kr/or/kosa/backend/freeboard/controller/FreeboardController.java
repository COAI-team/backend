package kr.or.kosa.backend.freeboard.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

import kr.or.kosa.backend.freeboard.domain.Freeboard;
import kr.or.kosa.backend.freeboard.service.FreeboardService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/freeboard")
public class FreeboardController {

    private final FreeboardService service;

    // 페이징 게시글 목록
    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return service.listPage(page, size);
    }

    // 게시글 상세 보기
    @GetMapping("/{id}")
    public Freeboard detail(@PathVariable Long id) {
        return service.detail(id);
    }

    // 게시글 작성
    @PostMapping
    public void write(@RequestBody Freeboard board) {
        service.write(board);
    }

    // 게시글 수정
    @PutMapping
    public void edit(@RequestBody Freeboard board) {
        service.edit(board);
    }

    // 게시글 삭제
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
