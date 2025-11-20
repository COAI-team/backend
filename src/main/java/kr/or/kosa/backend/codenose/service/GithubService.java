package kr.or.kosa.backend.codenose.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.codenose.dto.GithubFileResponseDto;
import kr.or.kosa.backend.codenose.dto.RepositoryDto;
import kr.or.kosa.backend.codenose.dto.BranchDto;
import kr.or.kosa.backend.codenose.dto.TreeEntryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GithubService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${github.token:}")
    private String githubToken;  // application.properties에 설정

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github.v3+json");
        if (githubToken != null && !githubToken.isEmpty()) {
            headers.set("Authorization", "Bearer " + githubToken);
        }
        return headers;
    }

    public List<RepositoryDto> listRepositories() {
        try {
            String url = "https://api.github.com/user/repos";
            HttpEntity<String> entity = new HttpEntity<>(createHeaders());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode jsonNode = objectMapper.readTree(response.getBody());

            List<RepositoryDto> repositories = new ArrayList<>();
            if (jsonNode.isArray()) {
                for (JsonNode repoNode : jsonNode) {
                    String name = repoNode.get("name").asText();
                    String fullName = repoNode.get("full_name").asText();
                    String repoUrl = repoNode.get("html_url").asText();
                    String owner = repoNode.get("owner").get("login").asText();
                    repositories.add(new RepositoryDto(name, fullName, repoUrl, owner));
                }
            }
            return repositories;
        } catch (Exception e) {
            log.error("GitHub 레포지토리 목록 조회 실패: {}", e.getMessage());
            throw new RuntimeException("레포지토리 목록을 가져오는데 실패했습니다: " + e.getMessage());
        }
    }

    public List<BranchDto> listBranches(String owner, String repo) {
        try {
            String url = String.format("https://api.github.com/repos/%s/%s/branches", owner, repo);
            HttpEntity<String> entity = new HttpEntity<>(createHeaders());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode jsonNode = objectMapper.readTree(response.getBody());

            List<BranchDto> branches = new ArrayList<>();
            if (jsonNode.isArray()) {
                for (JsonNode branchNode : jsonNode) {
                    String name = branchNode.get("name").asText();
                    branches.add(new BranchDto(name));
                }
            }
            return branches;
        } catch (Exception e) {
            log.error("GitHub 브랜치 목록 조회 실패: {}", e.getMessage());
            throw new RuntimeException("브랜치 목록을 가져오는데 실패했습니다: " + e.getMessage());
        }
    }

    public List<TreeEntryDto> getTree(String owner, String repo, String branch) {
        try {
            String url = String.format("https://api.github.com/repos/%s/%s/git/trees/%s?recursive=1", owner, repo, branch);
            HttpEntity<String> entity = new HttpEntity<>(createHeaders());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode jsonNode = objectMapper.readTree(response.getBody());

            List<TreeEntryDto> tree = new ArrayList<>();
            if (jsonNode.has("tree") && jsonNode.get("tree").isArray()) {
                for (JsonNode treeNode : jsonNode.get("tree")) {
                    String path = treeNode.get("path").asText();
                    String type = treeNode.get("type").asText();
                    tree.add(new TreeEntryDto(path, type));
                }
            }
            return tree;
        } catch (Exception e) {
            log.error("GitHub 파일 트리 조회 실패: {}", e.getMessage());
            throw new RuntimeException("파일 트리를 가져오는데 실패했습니다: " + e.getMessage());
        }
    }


    public GithubFileResponseDto getFileContent(String owner, String repo, String path) {
        try {
            String url = String.format("https://api.github.com/repos/%s/%s/contents/%s",
                    owner, repo, path);
            HttpEntity<String> entity = new HttpEntity<>(createHeaders());

            // API 호출
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

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

            return new GithubFileResponseDto(name, filePath, decodedContent, encoding, size);

        } catch (Exception e) {
            log.error("GitHub API 호출 실패: {}", e.getMessage());
            throw new RuntimeException("파일을 가져오는데 실패했습니다: " + e.getMessage());
        }
    }
}