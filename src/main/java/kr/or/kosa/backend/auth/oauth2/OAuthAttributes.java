package kr.or.kosa.backend.auth.oauth2;

import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import kr.or.kosa.backend.users.exception.UserErrorCode;
import lombok.Builder;

import java.util.Map;

@Builder
public record OAuthAttributes(String provider, String providerId, String email, String name, String picture) {

    public static OAuthAttributes of(String provider, Map<String, Object> attributes) {

        if (!"github".equals(provider)) {
            throw new CustomBusinessException(UserErrorCode.UNSUPPORTED_OAUTH_PROVIDER);
        }

        return ofGithub(provider, attributes);
    }

    // ---------------------------------------------------------
    // GITHUB
    // ---------------------------------------------------------
    private static OAuthAttributes ofGithub(String provider, Map<String, Object> attributes) {

        return OAuthAttributes.builder()
                .provider(provider)
                .providerId(String.valueOf(attributes.get("id")))
                .email((String) attributes.get("email")) // GitHub는 email이 null일 수 있음
                .name((String) attributes.get("name"))
                .picture((String) attributes.get("avatar_url"))
                .build();
    }
}