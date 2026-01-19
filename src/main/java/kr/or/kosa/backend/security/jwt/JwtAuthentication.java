package kr.or.kosa.backend.security.jwt;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.io.Serial;
import java.util.Objects;

public class JwtAuthentication extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    private final JwtUserDetails principal;  // ✔ principal = JwtUserDetails
    private final JwtUserDetails details;    // ✔ details = JwtUserDetails

    public JwtAuthentication(JwtUserDetails userDetails) {
        super(null); // 권한 없음
        this.principal = userDetails;
        this.details = userDetails;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public JwtUserDetails getPrincipal() {
        return principal;
    }

    @Override
    public JwtUserDetails getDetails() {
        return details;
    }

    @Override
    public String getName() {
        return String.valueOf(principal.id());
    }

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