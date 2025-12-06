package kr.or.kosa.backend.auth.github.controller;

import kr.or.kosa.backend.auth.github.dto.GitHubCallbackResponse;
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

import java.util.HashMap;
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

    private static final String KEY_GITHUB_ID = "githubId";
    private static final String KEY_GITHUB_LOGIN = "githubLogin";
    private static final String KEY_AVATAR_URL = "avatarUrl";

    /**
     * 🔥 GitHub OAuth Callback
     */
    @GetMapping("/callback")
    public ResponseEntity<GitHubCallbackResponse> callback(
            @RequestParam("code") String code,
            @RequestParam(value = "mode", required = false) String mode
    ) {
        GitHubUserResponse gitHubUser = gitHubOAuthService.getUserInfo(code);

        boolean linkMode = "link".equals(mode);  // ← 🔥 이 줄이 반드시 필요
        
        // 🔥 연동 모드 처리
        if (linkMode) {
            return ResponseEntity.ok(
                    GitHubCallbackResponse.builder()
                            .linkMode(true)
                            .gitHubUser(gitHubUser)
                            .build()
            );
        }

        // 🔥 로그인 처리
        Users user = userService.githubLogin(gitHubUser, linkMode);

        String accessToken = jwtProvider.createAccessToken(user.getUserId(), user.getUserEmail());
        String refreshToken = jwtProvider.createRefreshToken(user.getUserId(), user.getUserEmail());

        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + user.getUserId(),
                refreshToken,
                REFRESH_TOKEN_EXPIRE_DAYS,
                TimeUnit.DAYS
        );

        UserLoginResponseDto loginDto = UserLoginResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(user.toDto())
                .build();

        return ResponseEntity.ok(
                GitHubCallbackResponse.builder()
                        .linkMode(false)
                        .loginResponse(loginDto)
                        .build()
        );
    }

    /**
     * 🔍 GitHub 연동 정보 조회
     */
    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> getGithubUserInfo(
            @RequestHeader("Authorization") String token
    ) {
        String accessToken = token.replace("Bearer ", "");
        Long userId = jwtProvider.getUserIdFromToken(accessToken);

        boolean linked = userService.isGithubLinked(userId);

        if (!linked) {
            Map<String, Object> body = new HashMap<>();
            body.put("linked", false);
            body.put(KEY_GITHUB_ID, null);
            body.put(KEY_GITHUB_LOGIN, null);
            body.put(KEY_AVATAR_URL, null);

            return ResponseEntity.ok(body);
        }

        Map<String, Object> githubInfo = userService.getGithubUserInfo(userId);

        Map<String, Object> body = new HashMap<>();
        body.put("linked", true);
        body.put(KEY_GITHUB_ID, githubInfo.get(KEY_GITHUB_ID));
        body.put(KEY_GITHUB_LOGIN, githubInfo.get(KEY_GITHUB_LOGIN));
        body.put(KEY_AVATAR_URL, githubInfo.get(KEY_AVATAR_URL));

        return ResponseEntity.ok(body);
    }

    /**
     * 🔌 GitHub 연동 해제
     */
    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, Object>> disconnectGithub(
            @RequestHeader("Authorization") String token
    ) {
        String accessToken = token.replace("Bearer ", "");
        Long userId = jwtProvider.getUserIdFromToken(accessToken);

        boolean result = userService.disconnectGithub(userId);

        return ResponseEntity.ok(
                Map.of(
                        KEY_SUCCESS, result,
                        KEY_MESSAGE, result
                                ? "GitHub 연결이 해제되었습니다."
                                : "GitHub 연결 해제에 실패했습니다."
                )
        );
    }
}