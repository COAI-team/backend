package kr.or.kosa.backend.auth.oauth2;

import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import kr.or.kosa.backend.users.exception.UserErrorCode;

import java.util.Map;

public record OAuthAttributes(String provider, String providerId, String email, String name, String picture) {

    private static final String KEY_ID = "id";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_NAME = "name";
    private static final String KEY_AVATAR_URL = "avatar_url";

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

        Object id = attributes.get(KEY_ID);
        return new OAuthAttributes(
                provider,
                id != null ? String.valueOf(id) : null,
                (String) attributes.get(KEY_EMAIL), // GitHub는 email이 null일 수 있음
                (String) attributes.get(KEY_NAME),
                (String) attributes.get(KEY_AVATAR_URL)
        );
    }
}
