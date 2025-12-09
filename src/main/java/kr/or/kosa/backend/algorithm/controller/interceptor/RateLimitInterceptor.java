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

        // GET 요청은 제한 없음
        if ("GET".equalsIgnoreCase(method)) {
            return true;
        }

        // UsageType 결정
        UsageType usageType = determineUsageType(uri);
        if (usageType == null) {
            return true;  // 제한 대상이 아님
        }

        // 인증된 사용자 확인
        Long userId = getCurrentUserId();
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
        if (uri.contains("/generate")) {
            return UsageType.GENERATE;
        } else if (uri.contains("/solve") || uri.contains("/submit")) {
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
