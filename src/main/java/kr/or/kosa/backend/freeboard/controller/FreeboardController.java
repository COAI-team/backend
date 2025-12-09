package kr.or.kosa.backend.freeboard.controller;

import jakarta.validation.Valid;
import kr.or.kosa.backend.commons.pagination.PageResponse;
import kr.or.kosa.backend.commons.pagination.SortDirection;
import kr.or.kosa.backend.commons.response.ApiResponse;
import kr.or.kosa.backend.freeboard.dto.*;
import kr.or.kosa.backend.freeboard.service.FreeboardService;
import kr.or.kosa.backend.freeboard.sort.FreeboardSortType;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Log4j2
@RestController
@RequestMapping("/freeboard")
@RequiredArgsConstructor
public class FreeboardController {

    private final FreeboardService freeboardService;

    // 게시글 목록 조회 (검색/정렬 포함) - PathVariable보다 먼저 선언
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<FreeboardListResponseDto>>> getList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "CREATED_AT") String sort,
            @RequestParam(defaultValue = "DESC") SortDirection direction,
            @RequestParam(required = false) String keyword
    ) {
        FreeboardSortType sortType = FreeboardSortType.from(sort);
        PageResponse<FreeboardListResponseDto> response =
                freeboardService.getList(page, size, sortType, direction, keyword);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 게시글 작성
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @Valid @RequestBody FreeboardCreateRequest request,
            @RequestAttribute("userId") Long userId
    ) {
        Long freeboardId = freeboardService.write(request.toDto(), userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("freeboardId", freeboardId)));
    }

    // 게시글 상세 조회 - PathVariable은 나중에 선언
    @GetMapping("/{freeboardId}")
    public ResponseEntity<ApiResponse<FreeboardDetailResponseDto>> get(
            @PathVariable Long freeboardId,
            @RequestAttribute(value = "userId", required = false) Long userId
    ) {
        FreeboardDetailResponseDto freeboard = freeboardService.detail(freeboardId, userId);
        return ResponseEntity.ok(ApiResponse.success(freeboard));
    }

    // 게시글 수정
    @PutMapping("/{freeboardId}")
    public ResponseEntity<ApiResponse<Void>> update(
            @PathVariable Long freeboardId,
            @Valid @RequestBody FreeboardUpdateRequest request,
            @RequestAttribute("userId") Long userId
    ) {
        freeboardService.edit(freeboardId, request.toDto(), userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // 게시글 삭제
    @DeleteMapping("/{freeboardId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long freeboardId,
            @RequestAttribute("userId") Long userId
    ) {
        freeboardService.delete(freeboardId, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}