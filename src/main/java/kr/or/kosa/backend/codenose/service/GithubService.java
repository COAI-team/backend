package kr.or.kosa.backend.codenose.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.codenose.dto.*;
import kr.or.kosa.backend.codenose.dto.*;
import kr.or.kosa.backend.codenose.mapper.AnalysisMapper;
import kr.or.kosa.backend.commons.util.EncryptionUtil;
import kr.or.kosa.backend.users.domain.Users;
import kr.or.kosa.backend.users.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * 깃허브 서비스 (GithubService)
 * 
 * 역할:
 * GitHub API와 직접 통신하여 레포지토리 목록, 브랜치, 파일 트리, 파일 내용을 가져옵니다.
 * 사용자의 GitHub OAuth 토큰을 해독(Decrypt)하여 인증 헤더에 사용하는 보안 로직을 포함합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GithubService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AnalysisMapper analysisMapper;
    private final UserMapper userMapper;
    private final EncryptionUtil encryptionUtil;

    @Value("${github.token:}")
    private String githubToken; // application.properties에 설정된 시스템 기본 토큰 (Fallback)

    /**
     * GitHub 인증 헤더 생성
     * 
     * 1. DB에서 사용자 정보를 조회합니다.
     * 2. 사용자의 암호화된 GitHub 토큰이 있다면 복호화하여 사용합니다.
     * 3. 없다면 시스템 기본 토큰을 사용하거나, 토큰 없이 요청을 시도합니다.
     * 
     * @param userId 요청한 사용자 ID
     * @return Authorization 헤더가 포함된 HttpHeaders
     */
    private HttpHeaders createHeaders(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github.v3+json");

        String tokenToUse = githubToken; // 기본값: 시스템 토큰

        if (userId != null) {
            Users user = userMapper.findById(userId);
            if (user != null) {
                log.debug("[Github] User found: id={}, githubId={}, hasToken={}", user.getUserId(), user.getGithubId(),
                        (user.getGithubToken() != null && !user.getGithubToken().isBlank()));

                // 사용자 토큰이 있으면 복호화 시도
                if (user.getGithubToken() != null && !user.getGithubToken().isBlank()) {
                    try {
                        tokenToUse = encryptionUtil.decrypt(user.getGithubToken());
                        log.debug("[Github] User token decrypted successfully.");
                    } catch (Exception e) {
                        log.error("[Github] Token decryption failed for user {}", userId, e);
                    }
                }
            } else {
                log.debug("[Github] User not found for id: {}", userId);
            }
        } else {
            log.debug("[Github] userId is null, using system token.");
        }

        if (tokenToUse != null && !tokenToUse.isEmpty()) {
            headers.set("Authorization", "Bearer " + tokenToUse);
            log.debug("[Github] Authorization header set with token (prefix: {}...)",
                    tokenToUse.substring(0, Math.min(4, tokenToUse.length())));
        } else {
            log.warn("[Github] No token available for GitHub API request. Rate limits may apply.");
        }
        return headers;
    }

    /**
     * 레포지토리 목록 조회
     * 
     * 사용자가 명시한 owner(또는 본인)의 레포지토리 목록을 가져옵니다.
     * 본인일 경우 'private' 등 모든 레포지토리를 포함한 내역(/user/repos)을 조회 시도합니다.
     */
    public List<GithubRepositoryDTO> listRepositories(Long userId, String owner) {
        try {
            String url;

            // 본인 확인 로직 (간소화됨): DB에 저장된 GitHub ID와 요청된 owner가 같으면 본인으로 간주
            Users user = (userId != null) ? userMapper.findById(userId) : null;
            boolean isMe = false;
            if (user != null && user.getGithubId() != null && user.getGithubId().equalsIgnoreCase(owner)) {
                isMe = true;
            }

            if (isMe) {
                // 내 레포지토리 전체 (Private 포함)
                url = "https://api.github.com/user/repos?type=all&per_page=100";
            } else {
                // 타인의 공개 레포지토리
                url = String.format("https://api.github.com/users/%s/repos?per_page=100", owner);
            }

            HttpEntity<String> entity = new HttpEntity<>(createHeaders(userId));
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode jsonNode = objectMapper.readTree(response.getBody());

            List<GithubRepositoryDTO> repositories = new ArrayList<>();
            if (jsonNode.isArray()) {
                for (JsonNode repoNode : jsonNode) {
                    String name = repoNode.get("name").asText();
                    String fullName = repoNode.get("full_name").asText();
                    String repoUrl = repoNode.get("html_url").asText();
                    String realOwner = repoNode.get("owner").get("login").asText();
                    repositories.add(new GithubRepositoryDTO(name, fullName, repoUrl, realOwner));
                }
            }
            return repositories;

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("GitHub API Error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("GitHub API Error: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("GitHub listRepositories failed", e);
            throw new RuntimeException("레포지토리 목록 조회 실패", e);
        }
    }

    /**
     * 브랜치 목록 조회
     */
    public List<GithubBranchDTO> listBranches(Long userId, String owner, String repo) {
        try {
            String url = String.format("https://api.github.com/repos/%s/%s/branches", owner, repo);
            HttpEntity<String> entity = new HttpEntity<>(createHeaders(userId));
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode jsonNode = objectMapper.readTree(response.getBody());

            List<GithubBranchDTO> branches = new ArrayList<>();
            if (jsonNode.isArray()) {
                for (JsonNode branchNode : jsonNode) {
                    String name = branchNode.get("name").asText();
                    branches.add(new GithubBranchDTO(name));
                }
            }
            return branches;
        } catch (Exception e) {
            log.error("GitHub listBranches failed", e);
            throw new RuntimeException("브랜치 목록 조회 실패", e);
        }
    }

    /**
     * 파일 트리(디렉토리) 조회
     * 
     * GitHub Git Data API의 Trees 엔드포인트를 사용하여, 해당 브랜치의 모든 파일 목록(recursive=1)을 가져옵니다.
     * 이를 통해 폴더 구조를 시각화할 수 있습니다.
     */
    public List<GithubTreeEntryDTO> getTree(Long userId, String owner, String repo, String branch) {
        try {
            String url = String.format("https://api.github.com/repos/%s/%s/git/trees/%s?recursive=1", owner, repo,
                    branch);
            HttpEntity<String> entity = new HttpEntity<>(createHeaders(userId));
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode jsonNode = objectMapper.readTree(response.getBody());

            List<GithubTreeEntryDTO> tree = new ArrayList<>();
            if (jsonNode.has("tree") && jsonNode.get("tree").isArray()) {
                for (JsonNode treeNode : jsonNode.get("tree")) {
                    String path = treeNode.get("path").asText();
                    String type = treeNode.get("type").asText();
                    tree.add(new GithubTreeEntryDTO(path, type));
                }
            }
            return tree;
        } catch (Exception e) {
            log.error("GitHub getTree failed", e);
            throw new RuntimeException("파일 트리 조회 실패", e);
        }
    }

    /**
     * 단일 파일 내용 조회
     * 
     * GitHub Contents API를 사용하여 파일의 Base64 인코딩된 내용을 가져온 후, 디코딩하여 반환합니다.
     */
    public GithubFileDTO getFileContent(Long userId, String owner, String repo, String path) {
        try {
            String url = String.format("https://api.github.com/repos/%s/%s/contents/%s",
                    owner, repo, path);
            HttpEntity<String> entity = new HttpEntity<>(createHeaders(userId));

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class);

            JsonNode jsonNode = objectMapper.readTree(response.getBody());

            String name = jsonNode.get("name").asText();
            String filePath = jsonNode.get("path").asText();
            String encodedContent = jsonNode.get("content").asText();
            String encoding = jsonNode.get("encoding").asText();
            int size = jsonNode.get("size").asInt();

            // 내용 디코딩 (GitHub API는 주로 Base64 사용)
            String decodedContent = new String(Base64.getDecoder()
                    .decode(encodedContent.replaceAll("\\s", "")));

            return new GithubFileDTO(name, filePath, decodedContent, encoding, size);

        } catch (Exception e) {
            log.error("GitHub getFileContent failed", e);
            throw new RuntimeException("파일 내용 조회 실패", e);
        }
    }

    /**
     * GitHub 파일 내용을 가져와서 DB에 저장
     * 
     * 분석 전 단계로, GitHub에서 파일을 가져와 DB의 GITHUB_FILES 테이블에 저장합니다.
     * 분석 로직은 항상 DB에 저장된 이 '스냅샷'을 기준으로 동작합니다.
     * 
     * @return 저장된 파일의 ID (UUID)
     */
    public String saveFileContentToDB(FileSaveRequestDTO request) {
        try {
            // 1. GitHub API로 파일 내용 가져오기
            GithubFileDTO fileData = getFileContent(
                    request.getUserId(),
                    request.getOwner(),
                    request.getRepo(),
                    request.getFilePath());

            // 2. DB 저장용 필드 설정
            fileData.setFileId(UUID.randomUUID().toString());
            fileData.setUserId(request.getUserId());
            fileData.setRepositoryUrl(request.getRepositoryUrl());
            fileData.setOwner(request.getOwner());
            fileData.setRepo(request.getRepo());
            fileData.setCreatedAt(LocalDateTime.now());
            fileData.setUpdatedAt(LocalDateTime.now());

            // 3. DB에 저장
            analysisMapper.saveFileContent(fileData);

            log.info("파일 저장 완료 - fileId: {}", fileData.getFileId());

            return fileData.getFileId();

        } catch (Exception e) {
            log.error("파일 저장 실패", e);
            throw new RuntimeException("파일 저장 실패: " + e.getMessage());
        }
    }
}