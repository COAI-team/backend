package kr.or.kosa.backend.freeboard.controller;

import jakarta.validation.Valid;
import kr.or.kosa.backend.commons.response.ApiResponse;
import kr.or.kosa.backend.freeboard.domain.Freeboard;
import kr.or.kosa.backend.freeboard.dto.FreeboardCreateRequest;
import kr.or.kosa.backend.freeboard.dto.FreeboardDto;
import kr.or.kosa.backend.freeboard.dto.FreeboardUpdateRequest;
import kr.or.kosa.backend.freeboard.service.FreeboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
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
    public ResponseEntity<Freeboard> get(@PathVariable Long freeboardId) {
        Freeboard freeboard = freeboardService.detail(freeboardId);
        return ResponseEntity.ok(freeboard);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Map<String, Object> result = freeboardService.listPage(page, size);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{freeboardId}")
    public ResponseEntity<Void> update(
            @PathVariable Long freeboardId,
            @Valid @RequestBody FreeboardUpdateRequest request,
            @RequestAttribute(value = "userId", required = false) Long userId
    ) {
        log.info("=== update 컨트롤러 시작 ===");
        log.info("freeboardId: {}", freeboardId);
        log.info("request: {}", request);
        log.info("request.getTags(): {}", request.getTags());

        Long actualUserId = (userId != null) ? userId : 1L;

        FreeboardDto dto = request.toDto();
        log.info("dto.getTags(): {}", dto.getTags());

        freeboardService.edit(freeboardId, dto, actualUserId);
        log.info("=== update 컨트롤러 완료 ===");

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