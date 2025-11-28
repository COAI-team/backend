package kr.or.kosa.backend.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtProvider {

    private final SecretKey key;

    private static final long ACCESS_TOKEN_EXP = 1000L * 60 * 30;        // 30분
    private static final long REFRESH_TOKEN_EXP = 1000L * 60 * 60 * 24 * 7; // 7일

    public JwtProvider(@Value("${jwt.secret}") String secretKey) {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    // =======================
    // 토큰 생성
    // =======================
    public String createAccessToken(Long id, String email) {
        return createToken(id, email, ACCESS_TOKEN_EXP);
    }

    public String createRefreshToken(Long id, String email) {
        return createToken(id, email, REFRESH_TOKEN_EXP);
    }

    private String createToken(Long id, String email, long exp) {
        return Jwts.builder()
                .setSubject(email)
                .claim("id", id)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + exp))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // =======================
    // 토큰 검증
    // =======================
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);

            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // =======================
    // claim 추출
    // =======================
    public Long getUserId(String token) {
        Claims claims = getClaims(token);
        return claims.get("id", Long.class);
    }

    public String getEmail(String token) {
        return getClaims(token).getSubject();
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // =======================
    // (1) 만료일 가져오기
    // =======================
    public Date getExpiration(String token) {
        return getClaims(token).getExpiration();
    }

    // =======================
    // (2) 남은 유효시간(ms) 계산
    // =======================
    public long getTokenRemainingTime(String token) {
        try {
            Date expiration = getExpiration(token);
            long now = System.currentTimeMillis();
            return expiration.getTime() - now;  // 남은 시간(ms)
        } catch (Exception e) {
            return 0;
        }
    }
}