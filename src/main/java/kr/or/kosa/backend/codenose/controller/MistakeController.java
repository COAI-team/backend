package kr.or.kosa.backend.codenose.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.or.kosa.backend.codenose.service.MistakeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/mistakes")
@RequiredArgsConstructor
@Tag(name = "Mistake API", description = "사용자 실수 통계 및 리포트 API")
public class MistakeController {

    private final MistakeService mistakeService;

    @Operation(summary = "경고 배너 표시 여부 확인")
    @GetMapping("/status/{userId}")
    public ResponseEntity<Boolean> checkAlertStatus(@PathVariable Long userId) {
        // 실제 로직: 30회 이상 반복된 실수가 있는지 확인
        return ResponseEntity.ok(mistakeService.checkAlertCondition(userId));
    }

    @Operation(summary = "실수 리포트 및 퀴즈 생성")
    @GetMapping("/report/{userId}")
    public ResponseEntity<String> getMistakeReport(@PathVariable Long userId) {
        String reportJson = mistakeService.generateMistakeReport(userId);
        return ResponseEntity.ok(reportJson);
    }

    @Operation(summary = "퀴즈 통과 처리 (실수 차감)")
    @PostMapping("/solve/{userId}")
    public ResponseEntity<Void> solveMistake(
            @PathVariable Long userId,
            @RequestBody Map<String, String> payload) {
        String mistakeType = payload.get("mistakeType");
        mistakeService.solveMistake(userId, mistakeType);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "통합 퀴즈 통과 (모든 임계치 초과 실수 차감)")
    @PostMapping("/solve-quiz/{userId}")
    public ResponseEntity<Void> solveQuiz(@PathVariable Long userId) {
        mistakeService.solveAllCriticalMistakes(userId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "실수 상세 내역 조회 (코드 스니펫 포함)")
    @GetMapping("/details/{userId}")
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> getMistakeDetails(
            @PathVariable Long userId,
            @RequestParam(required = false) String mistakeType) {
        return ResponseEntity.ok(mistakeService.getMistakeDetails(userId, mistakeType));
    }
}
