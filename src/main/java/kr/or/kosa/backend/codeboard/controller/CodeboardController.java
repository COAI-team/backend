package kr.or.kosa.backend.codeboard.controller;

import jakarta.validation.Valid;
import kr.or.kosa.backend.codeboard.dto.*;
import kr.or.kosa.backend.codeboard.service.CodeboardService;
import kr.or.kosa.backend.commons.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Log4j2
@RestController
@RequestMapping("/codeboard")
@RequiredArgsConstructor
public class CodeboardController {

    private final CodeboardService codeboardService;

    // 코드 게시글 생성
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @Valid @RequestBody CodeboardCreateRequest request,
            @RequestAttribute(value = "userId", required = false) Long userId
    ) {
        Long actualUserId = (userId != null) ? userId : 1L;
        Long codeboardId = codeboardService.write(request.toDto(), actualUserId);

        Map<String, Object> result = new HashMap<>();
        result.put("codeboardId", codeboardId);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // 코드 게시글 상세 조회
    @GetMapping("/{codeboardId}")
    public ResponseEntity<CodeboardDetailResponseDto> get(
            @PathVariable Long codeboardId,
            @RequestAttribute(value = "userId", required = false) Long userId
    ) {
        Long actualUserId = (userId != null) ? userId : 1L;
        CodeboardDetailResponseDto codeboard = codeboardService.detail(codeboardId, actualUserId);
        return ResponseEntity.ok(codeboard);
    }

    // 코드 게시판 목록 조회 (검색/정렬 기능 포함)
    @GetMapping("/list")
    public ResponseEntity<Page<CodeboardListResponseDto>> getList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(required = false) String keyword
    ) {
        Pageable pageable = createPageable(page - 1, size, sort);
        Page<CodeboardListResponseDto> posts = codeboardService.getList(pageable, keyword);

        return ResponseEntity.ok(posts);
    }

    // 정렬 조건에 따른 Pageable 생성
    private Pageable createPageable(int page, int size, String sort) {
        Sort sortOrder;

        switch (sort) {
            case "comments":
                sortOrder = Sort.by(Sort.Direction.DESC, "commentCount")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt"));
                break;
            case "likes":
                sortOrder = Sort.by(Sort.Direction.DESC, "likeCount")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt"));
                break;
            case "views":
                sortOrder = Sort.by(Sort.Direction.DESC, "click")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt"));
                break;
            case "latest":
            default:
                sortOrder = Sort.by(Sort.Direction.DESC, "createdAt");
                break;
        }

        return PageRequest.of(page, size, sortOrder);
    }

    // 코드 게시글 수정
    @PutMapping("/{codeboardId}")
    public ResponseEntity<Void> update(
            @PathVariable Long codeboardId,
            @Valid @RequestBody CodeboardUpdateRequest request,
            @RequestAttribute(value = "userId", required = false) Long userId
    ) {
        Long actualUserId = (userId != null) ? userId : 1L;
        CodeboardDto dto = request.toDto();
        codeboardService.edit(codeboardId, dto, actualUserId);

        return ResponseEntity.ok().build();
    }

    // 코드 게시글 삭제
    @DeleteMapping("/{codeboardId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long codeboardId,
            @RequestAttribute(value = "userId", required = false) Long userId
    ) {
        Long actualUserId = (userId != null) ? userId : 1L;
        codeboardService.delete(codeboardId, actualUserId);
        return ResponseEntity.ok().build();
    }
}