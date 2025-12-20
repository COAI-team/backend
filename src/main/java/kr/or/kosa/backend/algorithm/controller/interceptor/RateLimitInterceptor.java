package kr.or.kosa.backend.algorithm.controller.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.or.kosa.backend.algorithm.dto.enums.UsageType;
import kr.or.kosa.backend.algorithm.service.DailyMissionService;
import kr.or.kosa.backend.algorithm.service.RateLimitService;
import kr.or.kosa.backend.security.jwt.JwtAuthentication;
import kr.or.kosa.backend.security.jwt.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Rate Limiting 인터셉터
 * 무료 사용자는 일일 3회 제한, 구독자는 무제한
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;
    private final DailyMissionService dailyMissionService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        // GET 요청은 기본적으로 제한 없음 (단, SSE 스트리밍 엔드포인트는 예외)
        if ("GET".equalsIgnoreCase(method)) {
            // SSE 스트리밍 엔드포인트는 사용량 추적 대상
            // - /algo/problems/generate/validated/stream (문제 생성)
            // - /algo/pool/draw/stream (풀에서 문제 뽑기)
            if (!uri.contains("/stream")) {
                return true;
            }
            log.debug("SSE 스트리밍 요청 감지 - Rate limit 적용: {}", uri);
        }

        // UsageType 결정
        UsageType usageType = determineUsageType(uri);
        if (usageType == null) {
            return true;  // 제한 대상이 아님
        }

        // 인증된 사용자 확인
        Long userId = getCurrentUserId();

        // SSE 요청은 EventSource API 제한으로 Authorization 헤더 전송 불가
        // → 쿼리 파라미터에서 userId 읽기 시도
        if (userId == null && "GET".equalsIgnoreCase(method) && uri.contains("/stream")) {
            userId = getUserIdFromQueryParam(request);
            if (userId != null) {
                log.debug("SSE 요청 - 쿼리 파라미터에서 userId 획득: {}", userId);
            }
        }

        if (userId == null) {
            sendErrorResponse(response, HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
            return false;
        }

        // 구독자 여부 확인
        boolean isSubscriber = dailyMissionService.isSubscriber(userId);

        // 사용량 체크 및 증가
        RateLimitService.UsageCheckResult result = rateLimitService.checkAndIncrementUsage(
                userId, usageType, isSubscriber);

        if (!result.allowed()) {
            log.info("Rate limit 초과 - userId: {}, type: {}, usage: {}/{}",
                    userId, usageType, result.currentUsage(), result.dailyLimit());
            sendErrorResponse(response, HttpStatus.TOO_MANY_REQUESTS, result.message());
            return false;
        }

        log.debug("Rate limit 통과 - userId: {}, type: {}, remaining: {}",
                userId, usageType, result.remaining());
        return true;
    }

    /**
     * URI에서 UsageType 결정
     */
    private UsageType determineUsageType(String uri) {
        // 문제 생성 관련 엔드포인트
        if (uri.contains("/generate") || uri.contains("/pool/draw")) {
            return UsageType.GENERATE;
        }
        // 문제 풀이/제출 관련 엔드포인트
        if (uri.contains("/solve") || uri.contains("/submit") || uri.contains("/submissions")) {
            return UsageType.SOLVE;
        }
        return null;
    }

    /**
     * 현재 인증된 사용자 ID 조회
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthentication jwtAuth) {
            Object principal = jwtAuth.getPrincipal();
            if (principal instanceof JwtUserDetails userDetails) {
                return userDetails.id().longValue();
            }
        }
        return null;
    }

    /**
     * 쿼리 파라미터에서 userId 조회 (SSE 요청용)
     * EventSource API는 Authorization 헤더 전송 불가하므로 쿼리 파라미터로 대체
     */
    private Long getUserIdFromQueryParam(HttpServletRequest request) {
        String userIdParam = request.getParameter("userId");
        if (userIdParam != null && !userIdParam.isEmpty()) {
            try {
                return Long.parseLong(userIdParam);
            } catch (NumberFormatException e) {
                log.warn("잘못된 userId 파라미터: {}", userIdParam);
            }
        }
        return null;
    }

    /**
     * 에러 응답 전송
     */
    private void sendErrorResponse(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", status.name());
        errorResponse.put("message", message);

        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            errorResponse.put("upgradeUrl", "/subscription");
        }

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
