package kr.or.kosa.backend.auth.oauth2;

import kr.or.kosa.backend.users.domain.Users;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public record CustomUserPrincipal(Users user, Map<String, Object> attributes, String provider) implements OAuth2User {

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList(); // JWT 기반이면 권한 필요 없음
    }

    @Override
    public String getName() {
        return user.getUserEmail();
    }
}
