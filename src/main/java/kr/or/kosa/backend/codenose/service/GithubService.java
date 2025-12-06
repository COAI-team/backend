package kr.or.kosa.backend.codenose.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.codenose.dto.dtoReal.*;
import kr.or.kosa.backend.codenose.mapper.AnalysisMapper;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class GithubService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AnalysisMapper analysisMapper;

    @Value("${github.token:}")
    private String githubToken; // application.properties에 설정

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github.v3+json");
        if (githubToken != null && !githubToken.isEmpty()) {
            headers.set("Authorization", "Bearer " + githubToken);
        }
        return headers;
    }

    public List<GithubRepositoryDTO> listRepositories(String owner) {
        System.out.println("[TRACE] GithubService.listRepositories called with owner: " + owner);
        try {
            // 수정: users/ 추가
            String url = String.format("https://api.github.com/users/%s/repos", owner);
            HttpEntity<String> entity = new HttpEntity<>(createHeaders());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode jsonNode = objectMapper.readTree(response.getBody());

            List<GithubRepositoryDTO> repositories = new ArrayList<>();
            if (jsonNode.isArray()) {
                for (JsonNode repoNode : jsonNode) {
                    String name = repoNode.get("name").asText();
                    String fullName = repoNode.get("full_name").asText();
                    String repoUrl = repoNode.get("html_url").asText();
                    repositories.add(new GithubRepositoryDTO(name, fullName, repoUrl, owner));
                }
            }
            return repositories;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("GitHub API Error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("GitHub API Error: " + e.getStatusCode() + " " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("GitHub 레포지토리 목록 조회 실패", e);
            throw new RuntimeException("레포지토리 목록을 가져오는데 실패했습니다: " + e.getMessage());
        }
    }

    public List<GithubBranchDTO> listBranches(String owner, String repo) {
        System.out.println("[TRACE] GithubService.listBranches called with owner: " + owner + ", repo: " + repo);
        try {
            String url = String.format("https://api.github.com/repos/%s/%s/branches", owner, repo);
            HttpEntity<String> entity = new HttpEntity<>(createHeaders());
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
            log.error("GitHub 브랜치 목록 조회 실패: {}", e.getMessage());
            throw new RuntimeException("브랜치 목록을 가져오는데 실패했습니다: " + e.getMessage());
        }
    }

    public List<GithubTreeEntryDTO> getTree(String owner, String repo, String branch) {
        System.out.println("[TRACE] GithubService.getTree called with owner: " + owner + ", repo: " + repo
                + ", branch: " + branch);
        try {
            String url = String.format("https://api.github.com/repos/%s/%s/git/trees/%s?recursive=1", owner, repo,
                    branch);
            HttpEntity<String> entity = new HttpEntity<>(createHeaders());
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
            log.error("GitHub 파일 트리 조회 실패: {}", e.getMessage());
            throw new RuntimeException("파일 트리를 가져오는데 실패했습니다: " + e.getMessage());
        }
    }

    public GithubFileDTO getFileContent(String owner, String repo, String path) {
        System.out.println("[TRACE] GithubService.getFileContent called with owner: " + owner + ", repo: " + repo
                + ", path: " + path);
        try {
            String url = String.format("https://api.github.com/repos/%s/%s/contents/%s",
                    owner, repo, path);
            HttpEntity<String> entity = new HttpEntity<>(createHeaders());

            // API 호출
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class);

            // JSON 파싱
            JsonNode jsonNode = objectMapper.readTree(response.getBody());

            String name = jsonNode.get("name").asText();
            String filePath = jsonNode.get("path").asText();
            String encodedContent = jsonNode.get("content").asText();
            String encoding = jsonNode.get("encoding").asText();
            int size = jsonNode.get("size").asInt();

            // Base64 디코딩
            String decodedContent = new String(Base64.getDecoder()
                    .decode(encodedContent.replaceAll("\\s", "")));

            return new GithubFileDTO(name, filePath, decodedContent, encoding, size);

        } catch (Exception e) {
            log.error("GitHub API 호출 실패: {}", e.getMessage());
            throw new RuntimeException("파일을 가져오는데 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * GitHub 파일 내용을 가져와서 DB에 저장
     * 
     * @param request 파일 저장 요청 DTO
     * @return 저장된 파일 데이터의 fileId
     */
    public String saveFileContentToDB(FileSaveRequestDTO request) {
        System.out.println("[TRACE] GithubService.saveFileContentToDB called with request: " + request);
        try {
            // 1. GitHub API로 파일 내용 가져오기
            GithubFileDTO fileData = getFileContent(
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

            log.info("파일 내용 저장 완료 - fileId: {}, filePath: {}, contentSize: {}",
                    fileData.getFileId(),
                    fileData.getFilePath(),
                    fileData.getFileContent().length());

            return fileData.getFileId();

        } catch (Exception e) {
            log.error("파일 저장 실패: {}", e.getMessage(), e);
            throw new RuntimeException("파일 저장에 실패했습니다: " + e.getMessage());
        }
    }
}