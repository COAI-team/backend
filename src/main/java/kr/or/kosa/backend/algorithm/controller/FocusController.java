package kr.or.kosa.backend.algorithm.controller;

import kr.or.kosa.backend.algorithm.domain.FocusSession;
import kr.or.kosa.backend.algorithm.service.FocusTrackingService;
import kr.or.kosa.backend.commons.response.ApiResponse;
import kr.or.kosa.backend.security.jwt.JwtAuthentication;
import kr.or.kosa.backend.security.jwt.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/algo/focus")
@RequiredArgsConstructor
public class FocusController {

    private final FocusTrackingService focusTrackingService;

    // 테스트용 인증 추출 메서드
    private Long extractUserId(JwtAuthentication authentication) {
        if (authentication == null) {
            log.warn("🧪 테스트 모드: authentication이 null이므로 기본 userId=1 사용");
            return 1L;
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof JwtUserDetails userDetails)) {
            log.warn("🧪 테스트 모드: principal이 JwtUserDetails가 아니므로 기본 userId=1 사용: {}", principal);
            return 1L;
        }

        Long userId = userDetails.id().longValue();
        log.debug("✅ 인증된 사용자 - userId: {}", userId);
        return userId;
    }

    /**
     * 집중 세션 시작
     * POST /algo/focus/start
     */
    @PostMapping("/start")
    public ResponseEntity<ApiResponse<FocusSession>> startSession(
            @RequestBody Map<String, Long> request,
            @AuthenticationPrincipal JwtAuthentication authentication) {
        log.info("👁️ [Focus Start] 세션 시작 요청 수신 - request: {}", request);
        Long userId = extractUserId(authentication);
        Long problemId = request.get("problemId");
        log.info("👁️ [Focus Start] userId: {}, problemId: {}", userId, problemId);

        FocusSession session = focusTrackingService.startSession(userId, problemId);
        return ResponseEntity.ok(ApiResponse.success(session));
    }

    /**
     * 실시간 이벤트 수신 (시선 이탈, 탭 전환 등)
     * POST /algo/focus/events
     */
    @PostMapping("/events")
    public ResponseEntity<ApiResponse<Void>> receiveEvent(
            @RequestBody Map<String, Object> eventData) {
        log.info("👁️ [Focus Event] 이벤트 수신 - eventData: {}", eventData);
        // eventData: { sessionId, type, details, duration, ... }
        String sessionId = (String) eventData.get("sessionId");
        String type = (String) eventData.get("type");
        log.info("👁️ [Focus Event] sessionId: {}, type: {}", sessionId, type);

        focusTrackingService.logEvent(sessionId, type, eventData);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 세션 종료
     * POST /algo/focus/end
     */
    @PostMapping("/end")
    public ResponseEntity<ApiResponse<FocusSession>> endSession(
            @RequestBody Map<String, String> request) {
        log.info("👁️ [Focus End] 세션 종료 요청 수신 - request: {}", request);
        String sessionId = request.get("sessionId");
        log.info("👁️ [Focus End] sessionId: {}", sessionId);

        FocusSession result = focusTrackingService.endSession(sessionId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
