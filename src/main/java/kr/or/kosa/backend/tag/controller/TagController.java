package kr.or.kosa.backend.tag.controller;

import kr.or.kosa.backend.tag.dto.TagAutocompleteDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

import kr.or.kosa.backend.tag.service.TagService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/tag")
public class TagController {

    private final TagService tagService;

    // 자동완성 검색
    @GetMapping("/autocomplete")
    public ResponseEntity<List<TagAutocompleteDto>> autocomplete(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "5") int limit
    ) {
        List<TagAutocompleteDto> results = tagService.searchTagsForAutocomplete(keyword, limit);
        return ResponseEntity.ok(results);
    }

    // 코드게시판 게시글의 태그 조회
    @GetMapping("/codeboard/{codeboardId}")
    public ResponseEntity<List<String>> getCodeboardTags(@PathVariable Long codeboardId) {
        List<String> tags = tagService.getCodeboardTags(codeboardId);
        return ResponseEntity.ok(tags);
    }

    // 자유게시판 게시글의 태그 조회
    @GetMapping("/freeboard/{freeboardId}")
    public ResponseEntity<List<String>> getFreeboardTags(@PathVariable Long freeboardId) {
        List<String> tags = tagService.getFreeboardTags(freeboardId);
        return ResponseEntity.ok(tags);
    }

    // 태그로 코드게시판 게시글 검색
    @GetMapping("/search/codeboard")
    public ResponseEntity<List<Long>> searchCodeboardByTag(@RequestParam String tag) {
        List<Long> codeboardIds = tagService.searchCodeboardByTag(tag);
        return ResponseEntity.ok(codeboardIds);
    }

    // 태그로 자유게시판 게시글 검색
    @GetMapping("/search/freeboard")
    public ResponseEntity<List<Long>> searchFreeboardByTag(@RequestParam String tag) {
        List<Long> freeboardIds = tagService.searchFreeboardByTag(tag);
        return ResponseEntity.ok(freeboardIds);
    }
}
