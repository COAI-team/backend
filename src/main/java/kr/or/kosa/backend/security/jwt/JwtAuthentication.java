package kr.or.kosa.backend.security.jwt;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Objects;

public class JwtAuthentication extends AbstractAuthenticationToken {

    private final JwtUserDetails principal;

    public JwtAuthentication(JwtUserDetails principal) {
        super(null);  // 권한 없음
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    // ======================================
    // equals & hashCode 추가 (경고 제거)
    // ======================================
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JwtAuthentication that)) return false;
        return Objects.equals(principal.id(), that.principal.id());
    }

    @Override
    public int hashCode() {
        return Objects.hash(principal.id());
    }
}