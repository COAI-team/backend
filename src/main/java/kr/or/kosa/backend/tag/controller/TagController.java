package kr.or.kosa.backend.tag.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import kr.or.kosa.backend.tag.domain.Tag;
import kr.or.kosa.backend.tag.dto.TagDTO;
import kr.or.kosa.backend.tag.service.TagService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tag")
public class TagController {

    private final TagService tagService;

    // 전체 태그 조회
    @GetMapping("/all")
    public List<Tag> all() {
        return tagService.getAllTags();
    }

    // 자유게시판 태그 조회
    @GetMapping("/freeboard/{id}")
    public List<Tag> getFreeboardTags(@PathVariable Long id) {
        return tagService.getTagsByFreeboardId(id);
    }

    // 코드게시판 태그 조회
    @GetMapping("/codeboard/{id}")
    public List<Tag> getCodeboardTags(@PathVariable Long id) {
        return tagService.getTagsByCodeboardId(id);
    }

    // 태그 등록
    @PostMapping
    public void addTag(@RequestBody Tag tag) {
        tagService.addTag(tag);
    }

    // 자유게시판 매핑 등록
    @PostMapping("/freeboard")
    public void addFreeboardTag(@RequestBody TagDTO dto) {
        tagService.addFreeboardTag(dto);
    }

    // 코드게시판 매핑 등록
    @PostMapping("/codeboard")
    public void addCodeboardTag(@RequestBody TagDTO dto) {
        tagService.addCodeboardTag(dto);
    }

    // 게시글 삭제 시 매핑 제거
    @DeleteMapping("/freeboard/{id}")
    public void deleteFreeboardTag(@PathVariable Long id) {
        tagService.deleteByFreeboardId(id);
    }

    @DeleteMapping("/codeboard/{id}")
    public void deleteCodeboardTag(@PathVariable Long id) {
        tagService.deleteByCodeboardId(id);
    }
}
