package kr.or.kosa.backend.codeboard.controller;

import jakarta.validation.Valid;
import kr.or.kosa.backend.codeboard.dto.*;
import kr.or.kosa.backend.codeboard.service.CodeboardService;
import kr.or.kosa.backend.codeboard.sort.CodeboardSortType;
import kr.or.kosa.backend.commons.pagination.PageResponse;
import kr.or.kosa.backend.commons.pagination.SortDirection;
import kr.or.kosa.backend.commons.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Log4j2
@RestController
@RequestMapping("/codeboard")
@RequiredArgsConstructor
public class CodeboardController {

    private final CodeboardService codeboardService;

    // 코드 게시판 목록 조회 (검색/정렬 기능 포함)
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CodeboardListResponseDto>>> getList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "CREATED_AT") String sort,
            @RequestParam(defaultValue = "DESC") SortDirection direction,
            @RequestParam(required = false) String keyword
    ) {
        CodeboardSortType sortType = CodeboardSortType.from(sort);
        PageResponse<CodeboardListResponseDto> response =
                codeboardService.getList(page, size, sortType, direction, keyword);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 코드 게시글 생성
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @Valid @RequestBody CodeboardCreateRequest request,
            @RequestAttribute("userId") Long userId
    ) {
        Long codeboardId = codeboardService.write(request.toDto(), userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("codeboardId", codeboardId)));
    }

    // 코드 게시글 상세 조회
    @GetMapping("/{codeboardId}")
    public ResponseEntity<ApiResponse<CodeboardDetailResponseDto>> get(
            @PathVariable Long codeboardId,
            @RequestAttribute(value = "userId", required = false) Long userId
    ) {
        CodeboardDetailResponseDto codeboard = codeboardService.detail(codeboardId, userId);
        return ResponseEntity.ok(ApiResponse.success(codeboard));
    }

    // 코드 게시글 수정
    @PutMapping("/{codeboardId}")
    public ResponseEntity<ApiResponse<Void>> update(
            @PathVariable Long codeboardId,
            @Valid @RequestBody CodeboardUpdateRequest request,
            @RequestAttribute("userId") Long userId
    ) {
        codeboardService.edit(codeboardId, request.toDto(), userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // 코드 게시글 삭제
    @DeleteMapping("/{codeboardId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long codeboardId,
            @RequestAttribute("userId") Long userId
    ) {
        codeboardService.delete(codeboardId, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}