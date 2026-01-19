package kr.or.kosa.backend.auth.github.controller;

import kr.or.kosa.backend.auth.github.dto.GitHubRepoDto;
import kr.or.kosa.backend.auth.github.dto.UserGithubSettingsDto;
import kr.or.kosa.backend.auth.github.service.UserGithubSettingsService;
import kr.or.kosa.backend.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * GitHub 자동커밋 설정 및 커밋 실행 API 컨트롤러
 *
 * 엔드포인트:
 * - GET  /api/github/settings              : 설정 조회
 * - POST /api/github/settings              : 설정 저장
 * - GET  /api/github/repositories          : 저장소 목록 조회
 * - POST /api/github/repositories/create   : 저장소 생성
 * - POST /api/github/repositories/select   : 저장소 선택
 * - POST /api/github/commit/{submissionId} : 커밋 실행
 *
 * @since 2025-12-13
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/github")
public class GitHubSettingsController {

    private final UserGithubSettingsService settingsService;
    private final JwtProvider jwtProvider;

    /**
     * GitHub 설정 조회
     */
    @GetMapping("/settings")
    public ResponseEntity<UserGithubSettingsDto> getSettings(
            @RequestHeader("Authorization") String token
    ) {
        Long userId = extractUserId(token);
        UserGithubSettingsDto settings = settingsService.getSettings(userId);
        return ResponseEntity.ok(settings);
    }

    /**
     * GitHub 설정 저장
     */
    @PostMapping("/settings")
    public ResponseEntity<Map<String, Object>> saveSettings(
            @RequestHeader("Authorization") String token,
            @RequestBody UserGithubSettingsDto settings
    ) {
        Long userId = extractUserId(token);
        UserGithubSettingsDto saved = settingsService.saveSettings(userId, settings);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "settings", saved
        ));
    }

    /**
     * 자동 커밋 토글
     */
    @PostMapping("/settings/auto-commit")
    public ResponseEntity<Map<String, Object>> toggleAutoCommit(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Boolean> request
    ) {
        Long userId = extractUserId(token);
        Boolean enabled = request.get("enabled");

        if (enabled == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "enabled 값이 필요합니다."
            ));
        }

        boolean result = settingsService.toggleAutoCommit(userId, enabled);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "autoCommitEnabled", result
        ));
    }

    /**
     * 사용자의 GitHub 저장소 목록 조회
     */
    @GetMapping("/repositories")
    public ResponseEntity<Map<String, Object>> listRepositories(
            @RequestHeader("Authorization") String token
    ) {
        Long userId = extractUserId(token);
        List<GitHubRepoDto> repositories = settingsService.listRepositories(userId);

        return ResponseEntity.ok(Map.of(
                "repositories", repositories
        ));
    }

    /**
     * 새 GitHub 저장소 생성
     */
    @PostMapping("/repositories/create")
    public ResponseEntity<Map<String, Object>> createRepository(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> request
    ) {
        Long userId = extractUserId(token);
        String repoName = (String) request.get("repoName");
        Boolean isPrivate = (Boolean) request.getOrDefault("isPrivate", true);

        if (repoName == null || repoName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "저장소 이름이 필요합니다."
            ));
        }

        GitHubRepoDto repo = settingsService.createRepository(userId, repoName, isPrivate);

        return ResponseEntity.status(201).body(Map.of(
                "success", true,
                "repository", repo
        ));
    }

    /**
     * 기존 저장소 선택
     */
    @PostMapping("/repositories/select")
    public ResponseEntity<Map<String, Object>> selectRepository(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> request
    ) {
        Long userId = extractUserId(token);
        String repoName = request.get("repoName");
        String repoUrl = request.get("repoUrl");

        if (repoName == null || repoUrl == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "저장소 정보가 필요합니다."
            ));
        }

        settingsService.updateRepository(userId, repoName, repoUrl);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "저장소가 선택되었습니다.",
                "repoName", repoName,
                "repoUrl", repoUrl
        ));
    }

    /**
     * 제출 결과 GitHub 커밋
     */
    @PostMapping("/commit/{submissionId}")
    public ResponseEntity<Map<String, Object>> commitSubmission(
            @RequestHeader("Authorization") String token,
            @PathVariable Long submissionId
    ) {
        Long userId = extractUserId(token);
        String commitUrl = settingsService.commitSubmission(userId, submissionId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "commitUrl", commitUrl != null ? commitUrl : ""
        ));
    }

    /**
     * JWT 토큰에서 userId 추출
     */
    private Long extractUserId(String token) {
        String accessToken = token.replace("Bearer ", "");
        return jwtProvider.getUserIdFromToken(accessToken);
    }
}
