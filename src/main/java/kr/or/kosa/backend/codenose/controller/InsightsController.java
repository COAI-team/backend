package kr.or.kosa.backend.codenose.controller;

import kr.or.kosa.backend.codenose.dto.CodeAnalysisHistoryDto;
import kr.or.kosa.backend.codenose.dto.UserCodePatternDto;
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
    public ResponseEntity<List<CodeAnalysisHistoryDto>> getAnalysisHistory(@PathVariable Long userId) {
        // In a real app, userId would come from the security context
        List<CodeAnalysisHistoryDto> history = insightsService.getAnalysisHistory(userId);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/patterns/{userId}")
    public ResponseEntity<List<UserCodePatternDto>> getUserPatterns(@PathVariable Long userId) {
        // In a real app, userId would come from the security context
        List<UserCodePatternDto> patterns = insightsService.getUserCodePatterns(userId);
        return ResponseEntity.ok(patterns);
    }
}
