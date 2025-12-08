package kr.or.kosa.backend.auth.oauth2;

import kr.or.kosa.backend.users.domain.Users;
import kr.or.kosa.backend.users.mapper.UserMapper;
import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import kr.or.kosa.backend.users.exception.UserErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserMapper userMapper;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {

        // 1) DefaultOAuth2UserService가 provider에서 정보 가져옴
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // 2) provider 이름 (google, github, naver…)
        String provider = userRequest.getClientRegistration()
                .getRegistrationId()
                .toLowerCase();

        // 3) provider 별 attribute 통일
        Map<String, Object> attributes = oAuth2User.getAttributes();
        OAuthAttributes oauth = OAuthAttributes.of(provider, attributes);

        String email = oauth.email();
        String providerId = oauth.providerId();
        String name = oauth.name();
        String picture = oauth.picture();

        // --------------------------------------------
        // CASE 1: SOCIAL_LOGIN에 이미 등록된 계정
        // --------------------------------------------
        Users linkedUser = userMapper.findBySocialProvider(provider, providerId);
        if (linkedUser != null) {
            return new CustomUserPrincipal(linkedUser, attributes, provider);
        }

        // --------------------------------------------
        // CASE 2: Users 테이블에 같은 이메일 계정 존재
        // --------------------------------------------
        Users existingUser = userMapper.findByEmail(email);

        if (existingUser != null) {

            // 기존 social provider 조회
            String existingProvider = userMapper.findSocialProviderByUserId(existingUser.getUserId());

            // CASE A: provider가 다르면 절대 자동 연결 금지 → 새로운 계정 생성
            if (existingProvider != null && !existingProvider.equals(provider)) {
                Users newUser = createNewSocialUser(provider, providerId, email, name, picture);
                return new CustomUserPrincipal(newUser, attributes, provider);
            }

            // provider 같으면 연동
            userMapper.insertSocialAccount(
                    existingUser.getUserId(),
                    provider,
                    providerId,
                    email
            );

            return new CustomUserPrincipal(existingUser, attributes, provider);
        }

        // --------------------------------------------
        // CASE 3: 이메일도 없으면 → 완전 새로운 유저 생성
        // --------------------------------------------
        Users newUser = createNewSocialUser(provider, providerId, email, name, picture);
        return new CustomUserPrincipal(newUser, attributes, provider);
    }


    /**
     * 🔥 신규 Social User 생성 로직
     */
    private Users createNewSocialUser(String provider, String providerId, String email,
                                      String name, String picture) {

        if (email == null) {
            email = provider + "-" + providerId + "@noemail.com";
        }

        Users newUser = new Users();
        newUser.setUserEmail(email);
        newUser.setUserName(name != null ? name : provider + "User");
        newUser.setUserNickname(name != null ? name : provider + "User");
        newUser.setUserImage(picture);
        newUser.setUserPw(UUID.randomUUID().toString());
        newUser.setUserRole("ROLE_USER");
        newUser.setUserEnabled(true);

        int result = userMapper.insertUser(newUser);

        if (result <= 0) {
            throw new CustomBusinessException(UserErrorCode.USER_CREATE_FAIL);
        }

        // SOCIAL_LOGIN INSERT
        userMapper.insertSocialAccount(
                newUser.getUserId(),
                provider,
                providerId,
                email
        );

        return newUser;
    }
}
