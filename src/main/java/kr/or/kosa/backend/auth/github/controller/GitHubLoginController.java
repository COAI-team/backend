package kr.or.kosa.backend.auth.github.controller;

import kr.or.kosa.backend.auth.github.dto.GitHubUserResponse;
import kr.or.kosa.backend.auth.github.service.GitHubOAuthService;
import kr.or.kosa.backend.security.jwt.JwtProvider;
import kr.or.kosa.backend.users.domain.Users;
import kr.or.kosa.backend.users.dto.UserLoginResponseDto;
import kr.or.kosa.backend.users.service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth/github")
public class GitHubLoginController {

    private final GitHubOAuthService gitHubOAuthService;
    private final UserService userService;
    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;

    private static final long REFRESH_TOKEN_EXPIRE_DAYS = 14;
    private static final String REFRESH_KEY_PREFIX = "auth:refresh:";

    private static final String KEY_SUCCESS = "success";
    private static final String KEY_MESSAGE = "message";

    /**
     * 🔗 Github Login URL 반환
     */
    @GetMapping("/login-url")
    public ResponseEntity<Map<String, String>> getGithubLoginUrl() {
        String loginUrl = gitHubOAuthService.getGithubAuthorizeUrl();
        return ResponseEntity.ok(Map.of("loginUrl", loginUrl));
    }

    /**
     * 🔥 Github OAuth Callback
     */
    @GetMapping("/callback")
    public ResponseEntity<UserLoginResponseDto> callback(@RequestParam("code") String code) {

        GitHubUserResponse gitHubUser = gitHubOAuthService.getUserInfo(code);
        Users user = userService.githubLogin(gitHubUser);

        String accessToken = jwtProvider.createAccessToken(user.getUserId(), user.getUserEmail());
        String refreshToken = jwtProvider.createRefreshToken(user.getUserId(), user.getUserEmail());

        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + user.getUserId(),
                refreshToken,
                REFRESH_TOKEN_EXPIRE_DAYS,
                TimeUnit.DAYS
        );

        return ResponseEntity.ok(
                UserLoginResponseDto.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .user(user.toDto())
                        .build()
        );
    }

    /**
     * 🔌 Github 연동 해제
     */
    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, Object>> disconnectGithub(
            @RequestHeader("Authorization") String token
    ) {
        String accessToken = token.replace("Bearer ", "");
        Long userId = jwtProvider.getUserIdFromToken(accessToken); // ✔ id claim 사용

        boolean result = userService.disconnectGithub(userId); // ✔ boolean 기반

        return ResponseEntity.ok(
                Map.of(
                        KEY_SUCCESS, result,
                        KEY_MESSAGE, result
                                ? "Github 연결이 해제되었습니다."
                                : "Github 연결 해제에 실패했습니다."
                )
        );
    }
}