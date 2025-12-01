package kr.or.kosa.backend.security.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.micrometer.common.lang.NonNull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String token = resolveToken(request);

        try {
            // 토큰이 있고 유효한 경우만 인증 처리
            if (token != null && jwtProvider.validateToken(token)) {

                Long userId = jwtProvider.getUserId(token);

                JwtUserDetails userDetails =
                        new JwtUserDetails(userId, jwtProvider.getEmail(token));

                JwtAuthentication auth =
                        new JwtAuthentication(userDetails);

                SecurityContextHolder.getContext().setAuthentication(auth);
            }

            // 정상 흐름 계속 진행
            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            // 🔥 Access Token 만료 → 401 응답
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");

            response.getWriter().write(
                    "{\"code\": \"TOKEN_EXPIRED\", \"message\": \"Access token expired\"}"
            );
        } catch (JwtException e) {
            // 🔥 기타 JWT 문제 → 401 or 400
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");

            response.getWriter().write(
                    "{\"code\": \"INVALID_TOKEN\", \"message\": \"Invalid JWT token\"}"
            );
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