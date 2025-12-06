package kr.or.kosa.backend.algorithm.controller;

import kr.or.kosa.backend.algorithm.dto.MonitoringSessionDto;
import kr.or.kosa.backend.algorithm.service.MonitoringService;
import kr.or.kosa.backend.commons.response.ApiResponse;
import kr.or.kosa.backend.security.jwt.JwtAuthentication;
import kr.or.kosa.backend.security.jwt.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 모니터링 컨트롤러
 *
 * 집중 모드(FOCUS)에서 사용되는 모니터링 API 제공
 * - 세션 시작/종료
 * - 위반 이벤트 수신
 * - 경고 기록
 *
 * 주요 변경점:
 * - 기존 /algo/focus 경로에서 /algo/monitoring 으로 변경
 * - 모니터링 결과가 점수에 반영되지 않음
 */
@Slf4j
@RestController
@RequestMapping("/algo/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final MonitoringService monitoringService;

    /**
     * 인증된 사용자 ID 추출 (테스트 모드 지원)
     */
    private Long extractUserId(JwtAuthentication authentication) {
        if (authentication == null) {
            log.warn("🧪 테스트 모드: authentication이 null이므로 기본 userId=1 사용");
            return 1L;
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof JwtUserDetails userDetails)) {
            log.warn("🧪 테스트 모드: principal이 JwtUserDetails가 아니므로 기본 userId=1 사용");
            return 1L;
        }

        return userDetails.id().longValue();
    }

    /**
     * 모니터링 세션 시작 (집중 모드 진입)
     * POST /algo/monitoring/start
     *
     * Request Body:
     * {
     *   "problemId": 123,
     *   "timeLimitMinutes": 30
     * }
     */
    @PostMapping("/start")
    public ResponseEntity<ApiResponse<MonitoringSessionDto>> startSession(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal JwtAuthentication authentication) {

        Long userId = extractUserId(authentication);
        Long problemId = ((Number) request.get("problemId")).longValue();
        Integer timeLimitMinutes = ((Number) request.get("timeLimitMinutes")).intValue();

        log.info("🎯 [Monitoring Start] userId: {}, problemId: {}, timeLimit: {}분",
                userId, problemId, timeLimitMinutes);

        MonitoringSessionDto session = monitoringService.startSession(userId, problemId, timeLimitMinutes);
        return ResponseEntity.ok(ApiResponse.success(session));
    }

    /**
     * 위반 이벤트 수신 (실시간)
     * POST /algo/monitoring/violation
     *
     * Request Body:
     * {
     *   "sessionId": "uuid-...",
     *   "violationType": "GAZE_AWAY",  // GAZE_AWAY, SLEEPING, NO_FACE, MASK_DETECTED,
     *                                   // MULTIPLE_FACES, MOUSE_LEAVE, TAB_SWITCH, FULLSCREEN_EXIT
     *   "details": { ... }  // 선택적 추가 정보
     * }
     */
    @PostMapping("/violation")
    public ResponseEntity<ApiResponse<Void>> receiveViolation(
            @RequestBody Map<String, Object> request) {

        String sessionId = (String) request.get("sessionId");
        String violationType = (String) request.get("violationType");

        log.info("⚠️ [Violation] sessionId: {}, type: {}", sessionId, violationType);

        monitoringService.processViolation(sessionId, violationType, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 경고 팝업 표시 기록
     * POST /algo/monitoring/warning
     *
     * Request Body:
     * {
     *   "sessionId": "uuid-..."
     * }
     */
    @PostMapping("/warning")
    public ResponseEntity<ApiResponse<Void>> recordWarning(
            @RequestBody Map<String, String> request) {

        String sessionId = request.get("sessionId");
        log.info("⚡ [Warning Shown] sessionId: {}", sessionId);

        monitoringService.recordWarningShown(sessionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 세션 종료 (정상 제출)
     * POST /algo/monitoring/end
     *
     * Request Body:
     * {
     *   "sessionId": "uuid-...",
     *   "remainingSeconds": 300  // 남은 시간 (초)
     * }
     */
    @PostMapping("/end")
    public ResponseEntity<ApiResponse<MonitoringSessionDto>> endSession(
            @RequestBody Map<String, Object> request) {

        String sessionId = (String) request.get("sessionId");
        Integer remainingSeconds = request.get("remainingSeconds") != null
                ? ((Number) request.get("remainingSeconds")).intValue()
                : null;

        log.info("✅ [Session End] sessionId: {}, remainingSeconds: {}", sessionId, remainingSeconds);

        MonitoringSessionDto result = monitoringService.endSession(sessionId, remainingSeconds);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 시간 초과 자동 제출 처리
     * POST /algo/monitoring/timeout
     *
     * Request Body:
     * {
     *   "sessionId": "uuid-..."
     * }
     */
    @PostMapping("/timeout")
    public ResponseEntity<ApiResponse<MonitoringSessionDto>> handleTimeout(
            @RequestBody Map<String, String> request) {

        String sessionId = request.get("sessionId");
        log.info("⏰ [Timeout] sessionId: {}", sessionId);

        MonitoringSessionDto result = monitoringService.handleTimeout(sessionId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 세션 정보 조회
     * GET /algo/monitoring/{sessionId}
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<MonitoringSessionDto>> getSession(
            @PathVariable String sessionId) {

        log.info("🔍 [Get Session] sessionId: {}", sessionId);

        MonitoringSessionDto session = monitoringService.getSession(sessionId);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.success(session));
    }

    /**
     * 사용자의 활성 세션 조회
     * GET /algo/monitoring/active?problemId=123
     */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<MonitoringSessionDto>> getActiveSession(
            @RequestParam Long problemId,
            @AuthenticationPrincipal JwtAuthentication authentication) {

        Long userId = extractUserId(authentication);
        log.info("🔍 [Get Active Session] userId: {}, problemId: {}", userId, problemId);

        MonitoringSessionDto session = monitoringService.getActiveSession(userId, problemId);
        return ResponseEntity.ok(ApiResponse.success(session)); // null이어도 OK 반환
    }
}
