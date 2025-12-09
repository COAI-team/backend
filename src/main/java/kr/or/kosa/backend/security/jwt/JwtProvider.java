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

    private static final long ACCESS_TOKEN_EXP = 1000L * 60 * 30; // 30분
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
                .claim("id", id) // ✔ userId가 아니라 id 로 저장됨
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
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            System.out.println("[JwtProvider] Invalid JWT signature: " + e.getMessage());
        } catch (ExpiredJwtException e) {
            System.out.println("[JwtProvider] Expired JWT token: " + e.getMessage());
        } catch (UnsupportedJwtException e) {
            System.out.println("[JwtProvider] Unsupported JWT token: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.println("[JwtProvider] JWT claims string is empty: " + e.getMessage());
        } catch (JwtException e) {
            System.out.println("[JwtProvider] JWT Error: " + e.getMessage());
        }
        return false;
    }

    // =======================
    // claim 추출
    // =======================
    public Long getUserId(String token) {
        Claims claims = getClaims(token);
        return claims.get("id", Long.class); // ✔ 기존 로직 그대로
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
    // 만료일 가져오기
    // =======================
    public Date getExpiration(String token) {
        return getClaims(token).getExpiration();
    }

    // =======================
    // 남은 유효시간(ms)
    // =======================
    public long getTokenRemainingTime(String token) {
        try {
            Date expiration = getExpiration(token);
            return expiration.getTime() - System.currentTimeMillis();
        } catch (Exception e) {
            return 0;
        }
    }

    // =======================
    // GitHub Disconnect 용 메서드
    // =======================
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("id", Long.class); // ✔ userId → id 로 수정
    }

    // =======================
    // 공통 Claims 파싱 메서드 추가 (새로 추가됨)
    // =======================
    private Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}