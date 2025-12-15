package kr.or.kosa.backend.security.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.micrometer.common.lang.NonNull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // 🔥 JWT 검사 제외 경로
        if (path.startsWith("/auth/github/login-url") ||
                path.startsWith("/auth/github/callback") ||
                path.startsWith("/auth/github/user")) {

            filterChain.doFilter(request, response);
            return;
        }

        // 🔥 그 외의 경로는 JWT 검증
        String token = resolveToken(request);
        log.info("[JwtFilter] Request URI: {}, Token Present: {}", path, token != null);

        try {
            if (token != null) {
                boolean isValid = jwtProvider.validateToken(token);
                log.info("[JwtFilter] Token Valid: {}", isValid);

                if (isValid) {
                    Long userId = jwtProvider.getUserId(token);
                    String email = jwtProvider.getEmail(token);

                    JwtUserDetails userDetails = new JwtUserDetails(userId, email);

                    JwtAuthentication auth = new JwtAuthentication(userDetails);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    request.setAttribute("userId", userId);
                }
            }

            filterChain.doFilter(request, response);
            log.info("[JwtFilter] FilterChain passed for URI: {}", path);

        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT token: {}", e.getMessage());
            // Token is expired, but we let the request continue as Anonymous.
            // If the endpoint requires auth, SecurityConfig will block it (403).
            // If the endpoint is permitAll, it will succeed.
            filterChain.doFilter(request, response);
        } catch (JwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            // Token is invalid, treat as Anonymous.
            filterChain.doFilter(request, response);
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }

        return null;
    }
}