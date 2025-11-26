package kr.or.kosa.backend.freeboard.controller;

import jakarta.validation.Valid;
import kr.or.kosa.backend.freeboard.domain.Freeboard;
import kr.or.kosa.backend.freeboard.dto.FreeboardCreateRequest;
import kr.or.kosa.backend.freeboard.dto.FreeboardUpdateRequest;
import kr.or.kosa.backend.freeboard.service.FreeboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/freeboard")
@RequiredArgsConstructor
public class FreeboardController {

    private final FreeboardService freeboardService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody FreeboardCreateRequest request,
            @RequestAttribute(value = "userId", required = false) Long userId
    ) {
        Long actualUserId = (userId != null) ? userId : 1L;
        Long freeboardId = freeboardService.write(request.toDto(), actualUserId);

        Map<String, Object> result = new HashMap<>();
        result.put("freeboardId", freeboardId);

        return ResponseEntity.ok(result);
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
        Long actualUserId = (userId != null) ? userId : 1L;
        freeboardService.edit(freeboardId, request.toDto(), actualUserId);
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