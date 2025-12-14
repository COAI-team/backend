package kr.or.kosa.backend.auth.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import kr.or.kosa.backend.auth.github.dto.GitHubCommitResponseDto;
import kr.or.kosa.backend.auth.github.dto.GitHubRepoDto;
import kr.or.kosa.backend.auth.github.exception.GithubErrorCode;
import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * GitHub 커밋 서비스 구현체
 * GitHub REST API를 사용하여 저장소 관리 및 파일 커밋 수행
 *
 * @since 2025-12-13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubCommitServiceImpl implements GitHubCommitService {

    private static final String GITHUB_API_BASE = "https://api.github.com";

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 언어별 파일 확장자 매핑
     */
    private static final Map<String, String> LANGUAGE_EXTENSIONS = Map.ofEntries(
            Map.entry("Python", "py"),
            Map.entry("Java", "java"),
            Map.entry("JavaScript", "js"),
            Map.entry("TypeScript", "ts"),
            Map.entry("C++", "cpp"),
            Map.entry("C#", "cs"),
            Map.entry("Go", "go"),
            Map.entry("Kotlin", "kt"),
            Map.entry("Swift", "swift"),
            Map.entry("Rust", "rs"),
            Map.entry("SQLite", "sql")
    );

    @Override
    public List<GitHubRepoDto> listRepositories(String accessToken) {
        String url = GITHUB_API_BASE + "/user/repos?sort=updated&per_page=100";

        HttpHeaders headers = createHeaders(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<List<GitHubRepoDto>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<List<GitHubRepoDto>>() {}
            );
            return response.getBody() != null ? response.getBody() : new ArrayList<>();
        } catch (HttpClientErrorException e) {
            log.error("GitHub 저장소 목록 조회 실패: {}", e.getMessage());
            throw new CustomBusinessException(GithubErrorCode.GITHUB_API_ERROR);
        }
    }

    @Override
    public GitHubRepoDto createRepository(String accessToken, String repoName, String description, boolean isPrivate) {
        String url = GITHUB_API_BASE + "/user/repos";

        HttpHeaders headers = createHeaders(accessToken);

        Map<String, Object> body = new HashMap<>();
        body.put("name", repoName);
        body.put("description", description);
        body.put("private", isPrivate);
        body.put("auto_init", true); // README.md 자동 생성

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<GitHubRepoDto> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    GitHubRepoDto.class
            );
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("GitHub 저장소 생성 실패: {}", e.getMessage());
            throw new CustomBusinessException(GithubErrorCode.GITHUB_REPO_CREATE_FAILED);
        }
    }

    @Override
    public String commitSolution(
            String accessToken,
            String repoFullName,
            Long problemId,
            String problemTitle,
            String difficulty,
            String sourceCode,
            String languageName,
            String readmeContent
    ) {
        // 폴더명 생성: {난이도}/{문제번호}_{문제제목} (특수문자 제거)
        String sanitizedTitle = sanitizeFileName(problemTitle);
        String difficultyFolder = sanitizeDifficulty(difficulty);
        String problemFolder = problemId + "_" + sanitizedTitle;
        String folderPath = difficultyFolder + "/" + problemFolder;

        // 파일 확장자 결정
        String extension = getFileExtension(languageName);
        String sourceFileName = "Main." + extension;

        String commitUrl = null;

        try {
            // 1. 소스코드 파일 커밋
            String sourcePath = folderPath + "/" + sourceFileName;
            GitHubCommitResponseDto sourceResponse = commitFile(
                    accessToken,
                    repoFullName,
                    sourcePath,
                    sourceCode,
                    "[" + difficultyFolder + "] Add solution for problem " + problemId + ": " + problemTitle
            );

            if (sourceResponse != null && sourceResponse.getCommit() != null) {
                commitUrl = sourceResponse.getCommit().getHtmlUrl();
            }

            // 2. README.md 파일 커밋
            String readmePath = folderPath + "/README.md";
            commitFile(
                    accessToken,
                    repoFullName,
                    readmePath,
                    readmeContent,
                    "[" + difficultyFolder + "] Add README for problem " + problemId
            );

            return commitUrl;

        } catch (HttpClientErrorException e) {
            log.error("GitHub 커밋 실패: {}", e.getMessage());
            throw new CustomBusinessException(GithubErrorCode.GITHUB_COMMIT_FAILED);
        }
    }

    /**
     * 난이도 문자열 정리 (폴더명용)
     */
    private String sanitizeDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) {
            return "Unrated";
        }
        // 첫 글자 대문자, 나머지 소문자로 정규화
        String normalized = difficulty.trim();
        return normalized.substring(0, 1).toUpperCase() + normalized.substring(1).toLowerCase();
    }

    /**
     * GitHub에 단일 파일 커밋
     */
    private GitHubCommitResponseDto commitFile(
            String accessToken,
            String repoFullName,
            String filePath,
            String content,
            String commitMessage
    ) {
        String url = GITHUB_API_BASE + "/repos/" + repoFullName + "/contents/" + filePath;

        HttpHeaders headers = createHeaders(accessToken);

        // 파일 내용을 Base64로 인코딩
        String encodedContent = Base64.getEncoder().encodeToString(
                content.getBytes(StandardCharsets.UTF_8)
        );

        Map<String, Object> body = new HashMap<>();
        body.put("message", commitMessage);
        body.put("content", encodedContent);

        // 기존 파일이 있는지 확인하여 SHA 추가
        String existingSha = getFileSha(accessToken, repoFullName, filePath);
        if (existingSha != null) {
            body.put("sha", existingSha);
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<GitHubCommitResponseDto> response = restTemplate.exchange(
                url,
                HttpMethod.PUT,
                entity,
                GitHubCommitResponseDto.class
        );

        return response.getBody();
    }

    /**
     * 기존 파일의 SHA 조회 (업데이트 시 필요)
     */
    private String getFileSha(String accessToken, String repoFullName, String filePath) {
        String url = GITHUB_API_BASE + "/repos/" + repoFullName + "/contents/" + filePath;

        HttpHeaders headers = createHeaders(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    JsonNode.class
            );

            JsonNode bodyNode = response.getBody();
            if (bodyNode != null && bodyNode.has("sha")) {
                return bodyNode.get("sha").asText();
            }
        } catch (HttpClientErrorException.NotFound e) {
            // 파일이 없는 경우 (새 파일)
            return null;
        } catch (HttpClientErrorException e) {
            log.warn("파일 SHA 조회 실패: {}", e.getMessage());
        }

        return null;
    }

    @Override
    public String generateReadmeContent(
            Long problemId,
            String problemTitle,
            String problemDescription,
            String difficulty,
            String judgeResult,
            Integer executionTime,
            Integer memoryUsage,
            String aiFeedback
    ) {
        StringBuilder sb = new StringBuilder();

        // 제목
        sb.append("# ").append(problemId).append(" - ").append(problemTitle).append("\n\n");

        // 문제 정보
        sb.append("## 문제 정보\n");
        sb.append("- **난이도**: ").append(difficulty != null ? difficulty : "N/A").append("\n");
        sb.append("- **제출일**: ").append(LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        )).append("\n\n");

        // 채점 결과
        sb.append("## 채점 결과\n");
        sb.append("- **결과**: ").append(getResultEmoji(judgeResult)).append(" ").append(judgeResult).append("\n");
        if (executionTime != null) {
            sb.append("- **실행 시간**: ").append(executionTime).append("ms\n");
        }
        if (memoryUsage != null) {
            sb.append("- **메모리**: ").append(memoryUsage).append("KB\n");
        }
        sb.append("\n");

        // 문제 설명
        if (problemDescription != null && !problemDescription.isEmpty()) {
            sb.append("## 문제 설명\n");
            sb.append(problemDescription).append("\n\n");
        }

        // AI 피드백
        if (aiFeedback != null && !aiFeedback.isEmpty()) {
            sb.append("## AI 피드백\n");
            sb.append(aiFeedback).append("\n\n");
        }

        // 푸터
        sb.append("---\n");
        sb.append("> 이 문제는 [CoAI](https://coai.kr)에서 풀이되었습니다.\n");

        return sb.toString();
    }

    @Override
    public String getFileExtension(String languageName) {
        if (languageName == null) {
            return "txt";
        }
        return LANGUAGE_EXTENSIONS.getOrDefault(languageName, "txt");
    }

    /**
     * GitHub API 요청용 헤더 생성
     */
    private HttpHeaders createHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * 파일명에서 특수문자 제거
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "untitled";
        }
        // 파일명으로 사용할 수 없는 문자 제거
        return fileName
                .replaceAll("[\\\\/:*?\"<>|]", "")
                .replaceAll("\\s+", "_")
                .trim();
    }

    /**
     * 채점 결과에 따른 이모지 반환
     */
    private String getResultEmoji(String judgeResult) {
        if (judgeResult == null) {
            return "";
        }
        return switch (judgeResult.toUpperCase()) {
            case "AC" -> "✅";
            case "WA" -> "❌";
            case "TLE" -> "⏰";
            case "MLE" -> "💾";
            case "RE" -> "💥";
            case "CE" -> "🔧";
            default -> "❓";
        };
    }
}
