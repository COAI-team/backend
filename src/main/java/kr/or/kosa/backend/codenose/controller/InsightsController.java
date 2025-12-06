package kr.or.kosa.backend.codenose.controller;

import kr.or.kosa.backend.codenose.dto.dtoReal.CodeResultDTO;
import kr.or.kosa.backend.codenose.dto.dtoReal.UserCodePatternDTO;
import kr.or.kosa.backend.codenose.service.InsightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class InsightsController {

    private final InsightsService insightsService;

    @GetMapping("/history/{userId}")
    public ResponseEntity<List<CodeResultDTO>> getAnalysisHistory(@PathVariable Long userId) {
        System.out.println("[TRACE] InsightsController.getAnalysisHistory called with userId: " + userId);
        // In a real app, userId would come from the security context
        // List<CodeResultDTO> history = insightsService.getCodeResult(userId);
        List<CodeResultDTO> history = insightsService.getCodeResult(userId);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/patterns/{userId}")
    public ResponseEntity<List<UserCodePatternDTO>> getUserPatterns(@PathVariable Long userId) {
        System.out.println("[TRACE] InsightsController.getUserPatterns called with userId: " + userId);
        // In a real app, userId would come from the security context
        List<UserCodePatternDTO> patterns = insightsService.getUserCodePatterns(userId);
        return ResponseEntity.ok(patterns);
    }

    @GetMapping("/trends/{userId}")
    public ResponseEntity<java.util.Map<String, Object>> getPatternTrends(@PathVariable Long userId) {
        System.out.println("[TRACE] InsightsController.getPatternTrends called with userId: " + userId);
        return ResponseEntity.ok(insightsService.getPatternTrends(userId));
    }

    @GetMapping("/patterns/{userId}/detail")
    public ResponseEntity<List<java.util.Map<String, Object>>> getPatternDetails(
            @PathVariable Long userId,
            @RequestParam String pattern) {
        System.out.println(
                "[TRACE] InsightsController.getPatternDetails called with userId: " + userId + ", pattern: " + pattern);
        return ResponseEntity.ok(insightsService.getPatternDetails(userId, pattern));
    }
}
