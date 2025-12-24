package kr.or.kosa.backend.auth.github.controller;

import kr.or.kosa.backend.auth.github.dto.*;
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
    private static final String BEARER_PREFIX = "Bearer ";

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
            @RequestParam(value = "state", required = false) String mode
    ) {
        System.out.println("code: " + code);
        System.out.println("state: " + mode);
        // 1️⃣ GitHub 사용자 정보 조회
        GitHubUserResponse gitHubUser = gitHubOAuthService.getUserInfo(code);
        System.out.println("이거봐ㅏㅏㅏㅏㅏㅏ: " + gitHubUser.toString());
//        gitHubUser.setId(17L);
        // 2️⃣ 🔥 연동(link) 모드면 여기서 즉시 종료 (USER 생성 절대 금지)
        if ("link".equals(mode)) {
            return ResponseEntity.ok(
                    GitHubCallbackResponse.builder()
                            .linkMode(true)
                            .gitHubUser(gitHubUser)
                            .build()
            );
        }

        // 3️⃣ ⬇️ 이 아래는 "로그인 / 회원가입 전용" 로직
        GithubLoginResult result = userService.githubLogin(gitHubUser, false);
        Users user = result.getUser();

        // 4️⃣ 기존 일반 계정 존재 → 연동 유도
        if (result.isNeedLink()) {
            Tokens tokens = issueTokens(user);

            return ResponseEntity.ok(
                    GitHubCallbackResponse.builder()
                            .linkMode(false)
                            .needLink(true)
                            .userId(user.getUserId())
                            .message("기존 일반 계정이 존재합니다. GitHub 계정을 연동하시겠습니까?")
                            .gitHubUser(gitHubUser)
                            .accessToken(tokens.accessToken())
                            .refreshToken(tokens.refreshToken())
                            .build()
            );
        }

        // 5️⃣ 정상 GitHub 로그인 처리
        Tokens tokens = issueTokens(user);

        UserLoginResponseDto loginDto = UserLoginResponseDto.builder()
                .accessToken(tokens.accessToken())
                .refreshToken(tokens.refreshToken())
                .user(user.toDto())
                .build();

        return ResponseEntity.ok(
                GitHubCallbackResponse.builder()
                        .linkMode(false)
                        .needLink(false)
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
        String accessToken = token.replace(BEARER_PREFIX, "");
        Long userId = jwtProvider.getUserIdFromToken(accessToken);

        boolean linked = userService.isGithubLinked(userId);

        Map<String, Object> body = new HashMap<>(4);
        if (linked) {
            Map<String, Object> githubInfo = userService.getGithubUserInfo(userId);
            body.put("linked", true);
            body.put(KEY_GITHUB_ID, githubInfo.get(KEY_GITHUB_ID));
            body.put(KEY_GITHUB_LOGIN, githubInfo.get(KEY_GITHUB_LOGIN));
            body.put(KEY_AVATAR_URL, githubInfo.get(KEY_AVATAR_URL));
        } else {
            body.put("linked", false);
            body.put(KEY_GITHUB_ID, null);
            body.put(KEY_GITHUB_LOGIN, null);
            body.put(KEY_AVATAR_URL, null);
        }

        return ResponseEntity.ok(body);
    }

    /**
     * 🔌 GitHub 연동 해제
     */
    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, Object>> disconnectGithub(
            @RequestHeader("Authorization") String token
    ) {
        String accessToken = token.replace(BEARER_PREFIX, "");
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

    private Tokens issueTokens(Users user) {
        String accessToken = jwtProvider.createAccessToken(user.getUserId(), user.getUserEmail());
        String refreshToken = jwtProvider.createRefreshToken(user.getUserId(), user.getUserEmail());

        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + user.getUserId(),
                refreshToken,
                REFRESH_TOKEN_EXPIRE_DAYS,
                TimeUnit.DAYS
        );

        return new Tokens(accessToken, refreshToken);
    }

    private record Tokens(String accessToken, String refreshToken) {
    }

    @PostMapping(value = "/link", consumes = "application/json")  // ✅ consumes 추가!
    public ResponseEntity<GithubLinkResponse> linkGithub(
            @RequestHeader("Authorization") String token,
            @RequestBody GithubLinkRequest request
    ) {
        String accessToken = token.replace(BEARER_PREFIX, "");
        Long userId = jwtProvider.getUserIdFromToken(accessToken);
        System.out.println("---------모냐" + request.toString());
        boolean success = userService.linkGithubAccount(userId, request);  // ✅ boolean 반환 수정

        return ResponseEntity.ok(
                new GithubLinkResponse(success, "GitHub 계정이 연동되었습니다.")  // ✅ success 사용
        );
    }
}