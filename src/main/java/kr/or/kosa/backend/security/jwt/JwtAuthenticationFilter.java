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

        String path = request.getRequestURI();

        // GitHub OAuth(login) 경로는 JWT 검사 제외
        if (path.startsWith("/auth/github")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 그 외의 경로는 JWT 검증
        String token = resolveToken(request);

        try {
            if (token != null && jwtProvider.validateToken(token)) {
                Long userId = jwtProvider.getUserId(token);
                JwtUserDetails userDetails =
                        new JwtUserDetails(userId, jwtProvider.getEmail(token));

                JwtAuthentication auth = new JwtAuthentication(userDetails);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }

            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"code\": \"TOKEN_EXPIRED\", \"message\": \"Access token expired\"}"
            );
        } catch (JwtException e) {
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