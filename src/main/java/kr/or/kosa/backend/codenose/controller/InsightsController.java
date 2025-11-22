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
        // In a real app, userId would come from the security context
        List<CodeResultDTO> history = insightsService.getCodeResult(userId);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/patterns/{userId}")
    public ResponseEntity<List<UserCodePatternDTO>> getUserPatterns(@PathVariable Long userId) {
        // In a real app, userId would come from the security context
        List<UserCodePatternDTO> patterns = insightsService.getUserCodePatterns(userId);
        return ResponseEntity.ok(patterns);
    }
}
