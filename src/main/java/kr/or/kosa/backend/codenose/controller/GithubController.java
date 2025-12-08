package kr.or.kosa.backend.codenose.controller;

import kr.or.kosa.backend.codenose.dto.dtoReal.*;
import kr.or.kosa.backend.codenose.service.GithubService;
import kr.or.kosa.backend.commons.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/github")
@RequiredArgsConstructor
public class GithubController {

    private final GithubService githubService;

    @GetMapping("/repos")
    public ResponseEntity<List<GithubRepositoryDTO>> getRepositories(
            @RequestParam String owner) { // 또는 owner로 이름 맞추기
        System.out.println("[TRACE] GithubController.getRepositories called with owner: " + owner);
        List<GithubRepositoryDTO> repositories = githubService.listRepositories(owner);
        return ResponseEntity.ok(repositories);
    }

    @GetMapping("/repos/{owner}/{repo}/branches")
    public ResponseEntity<List<GithubBranchDTO>> getBranches(
            @PathVariable String owner,
            @PathVariable String repo) {
        System.out.println("[TRACE] GithubController.getBranches called with owner: " + owner + ", repo: " + repo);
        List<GithubBranchDTO> branches = githubService.listBranches(owner, repo);
        return ResponseEntity.ok(branches);
    }

    @GetMapping("/repos/{owner}/{repo}/tree")
    public ResponseEntity<List<GithubTreeEntryDTO>> getTree(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam String branch) {
        System.out.println("[TRACE] GithubController.getTree called with owner: " + owner + ", repo: " + repo
                + ", branch: " + branch);
        List<GithubTreeEntryDTO> tree = githubService.getTree(owner, repo, branch);
        return ResponseEntity.ok(tree);
    }

    @GetMapping("/repos/{owner}/{repo}/content")
    public ResponseEntity<GithubFileDTO> getFileContent(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam String path) {
        System.out.println("[TRACE] GithubController.getFileContent called with owner: " + owner + ", repo: " + repo
                + ", path: " + path);
        GithubFileDTO response = githubService.getFileContent(owner, repo, path);
        return ResponseEntity.ok(response);
    }

    /**
     * GitHub 파일 내용을 DB에 저장 (1단계: 파일 저장)
     * POST /api/github/save-file
     * Body: { "repositoryUrl": "...", "owner": "...", "repo": "...", "filePath":
     * "...", "userId": 1 }
     */
    @PostMapping("/save-file")
    public ResponseEntity<ApiResponse<Map<String, String>>> saveFileContent(
            @RequestBody FileSaveRequestDTO request) {
        System.out.println("[TRACE] GithubController.saveFileContent called with request: " + request);

        Long userId = 1L; // TODO: SecurityContext에서 가져오기
        request.setUserId(userId);

        String fileId = githubService.saveFileContentToDB(request);

        return ResponseEntity.ok(
                ApiResponse.success(Map.of(
                        "fileId", fileId, // analysisId -> fileId로 변경
                        "message", "파일 내용이 성공적으로 저장되었습니다.")));
    }
}
