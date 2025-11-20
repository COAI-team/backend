package kr.or.kosa.backend.codenose.controller;

import kr.or.kosa.backend.codenose.dto.BranchDto;
import kr.or.kosa.backend.codenose.dto.GithubFileResponseDto;
import kr.or.kosa.backend.codenose.dto.RepositoryDto;
import kr.or.kosa.backend.codenose.dto.TreeEntryDto;
import kr.or.kosa.backend.codenose.service.GithubService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/github")
@RequiredArgsConstructor
public class GithubController {

    private final GithubService githubService;

    @GetMapping("/repos")
    public ResponseEntity<List<RepositoryDto>> getRepositories() {
        List<RepositoryDto> repositories = githubService.listRepositories();
        return ResponseEntity.ok(repositories);
    }

    @GetMapping("/repos/{owner}/{repo}/branches")
    public ResponseEntity<List<BranchDto>> getBranches(
            @PathVariable String owner,
            @PathVariable String repo) {
        List<BranchDto> branches = githubService.listBranches(owner, repo);
        return ResponseEntity.ok(branches);
    }

    @GetMapping("/repos/{owner}/{repo}/tree/{branch}")
    public ResponseEntity<List<TreeEntryDto>> getTree(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable String branch) {
        List<TreeEntryDto> tree = githubService.getTree(owner, repo, branch);
        return ResponseEntity.ok(tree);
    }

    @GetMapping("/repos/{owner}/{repo}/content")
    public ResponseEntity<GithubFileResponseDto> getFileContent(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam String path) {
        GithubFileResponseDto response = githubService.getFileContent(owner, repo, path);
        return ResponseEntity.ok(response);
    }
}
