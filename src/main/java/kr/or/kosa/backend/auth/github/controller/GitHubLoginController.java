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

    /**
     * 🔥 GitHub OAuth Callback
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
     * 🔍 GitHub 연동 정보 조회 API
     * 👉 Users 엔티티에는 GitHub 정보가 없으므로
     * 👉 GitHub API를 직접 호출해 최신 정보를 가져온다.
     */
    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> getGithubUserInfo(
            @RequestHeader("Authorization") String token
    ) {
        String accessToken = token.replace("Bearer ", "");
        Long userId = jwtProvider.getUserIdFromToken(accessToken);

        boolean linked = userService.isGithubLinked(userId);

        // GitHub 계정 연동 안 했으면 null 값 반환
        if (!linked) {
            return ResponseEntity.ok(
                    Map.of(
                            "linked", false,
                            "githubId", null,
                            "githubLogin", null,
                            "avatarUrl", null
                    )
            );
        }

        // ⭐ JOIN 으로 얻은 GitHub 실제 정보 가져오기
        Map<String, Object> githubInfo = userService.getGithubUserInfo(userId);

        return ResponseEntity.ok(
                Map.of(
                        "linked", true,
                        "githubId", githubInfo.get("githubId"),
                        "githubLogin", githubInfo.get("githubLogin"),
                        "avatarUrl", githubInfo.get("avatarUrl")
                )
        );
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