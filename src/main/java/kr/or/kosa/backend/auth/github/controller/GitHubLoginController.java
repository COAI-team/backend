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

    @GetMapping("/callback")
    public ResponseEntity<?> callback(@RequestParam("code") String code) {

        try {
            // 1) GitHub API → 유저 정보
            GitHubUserResponse gitHubUser = gitHubOAuthService.getUserInfo(code);

            // 2) DB 처리 (신규 생성 / 자동 연동 / 기존 로그인)
            Users user = userService.githubLogin(gitHubUser);

            // 3) JWT 발급
            String accessToken = jwtProvider.createAccessToken(user.getUserId(), user.getUserEmail());
            String refreshToken = jwtProvider.createRefreshToken(user.getUserId(), user.getUserEmail());

            // 4) Redis에 refresh 저장
            redisTemplate.opsForValue().set(
                    REFRESH_KEY_PREFIX + user.getUserId(),
                    refreshToken,
                    REFRESH_TOKEN_EXPIRE_DAYS,
                    TimeUnit.DAYS
            );

            // 5) 성공 응답
            return ResponseEntity.ok(
                    UserLoginResponseDto.builder()
                            .accessToken(accessToken)
                            .refreshToken(refreshToken)
                            .user(user.toDto())
                            .build()
            );

        } catch (Exception e) {
            log.error("GitHub login failed: {}", e.getMessage());
            return ResponseEntity.status(500).body("GitHub Login Failed");
        }
    }
}