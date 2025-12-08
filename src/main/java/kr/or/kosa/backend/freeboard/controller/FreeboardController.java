package kr.or.kosa.backend.freeboard.controller;

import jakarta.validation.Valid;
import kr.or.kosa.backend.commons.response.ApiResponse;
import kr.or.kosa.backend.freeboard.dto.*;
import kr.or.kosa.backend.freeboard.service.FreeboardService;
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
@RequestMapping("/freeboard")
@RequiredArgsConstructor
public class FreeboardController {

    private final FreeboardService freeboardService;

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @Valid @RequestBody FreeboardCreateRequest request,
            @RequestAttribute(value = "userId", required = false) Long userId
    ) {
        Long actualUserId = (userId != null) ? userId : 1L;
        Long freeboardId = freeboardService.write(request.toDto(), actualUserId);

        Map<String, Object> result = new HashMap<>();
        result.put("freeboardId", freeboardId);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{freeboardId}")
    public ResponseEntity<FreeboardDetailResponseDto> get(
            @PathVariable Long freeboardId,
            @RequestAttribute(value = "userId", required = false) Long userId
    ) {
        Long actualUserId = (userId != null) ? userId : 1L;
        FreeboardDetailResponseDto freeboard = freeboardService.detail(freeboardId, actualUserId);
        return ResponseEntity.ok(freeboard);
    }

    // 자유게시판 목록 조회 (검색/정렬 기능 포함)
    @GetMapping("/list")
    public ResponseEntity<Page<FreeboardListResponseDto>> getList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(required = false) String keyword) {

        Pageable pageable = createPageable(page - 1, size, sort);
        Page<FreeboardListResponseDto> posts = freeboardService.getList(pageable, keyword);

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

    @PutMapping("/{freeboardId}")
    public ResponseEntity<Void> update(
            @PathVariable Long freeboardId,
            @Valid @RequestBody FreeboardUpdateRequest request,
            @RequestAttribute(value = "userId", required = false) Long userId
    ) {
        Long actualUserId = (userId != null) ? userId : 1L;
        FreeboardDto dto = request.toDto();
        freeboardService.edit(freeboardId, dto, actualUserId);

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{freeboardId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long freeboardId,
            @RequestAttribute(value = "userId", required = false) Long userId
    ) {
        Long actualUserId = (userId != null) ? userId : 1L;
        freeboardService.delete(freeboardId, actualUserId);
        return ResponseEntity.ok().build();
    }
}