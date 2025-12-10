package kr.or.kosa.backend.codenose.controller;

import kr.or.kosa.backend.codenose.dto.*;
import kr.or.kosa.backend.codenose.service.GithubService;
import kr.or.kosa.backend.commons.response.ApiResponse;
import kr.or.kosa.backend.security.jwt.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 깃허브 컨트롤러 (GithubController)
 * 
 * 역할:
 * GitHub API와 상호작용하는 프론트엔드의 요청을 중계(Proxy)하고,
 * 분석을 위해 필요한 파일 내용을 데이터베이스에 저장하는 역할을 수행합니다.
 */
@RestController
@RequestMapping("/api/github")
@RequiredArgsConstructor
public class GithubController {

    private final GithubService githubService;

    /**
     * 레포지토리 목록 조회
     * 
     * @param owner 레포지토리 소유자 (GitHub Username or Org)
     * @return 해당 소유자의 레포지토리 목록
     * 
     *         로직:
     *         사용자의 GitHub 토큰(DB 저장됨)을 사용하여 GitHub API를 호출합니다.
     */
    @GetMapping("/repos")
    public ResponseEntity<List<GithubRepositoryDTO>> getRepositories(
            @RequestParam String owner) {
        Long userId = getAuthenticatedUserId();
        List<GithubRepositoryDTO> repositories = githubService.listRepositories(userId, owner);
        return ResponseEntity.ok(repositories);
    }

    /**
     * 브랜치 목록 조회
     * 
     * @param owner 레포지토리 소유자
     * @param repo  레포지토리 이름
     * @return 해당 레포지토리의 브랜치 목록
     */
    @GetMapping("/repos/{owner}/{repo}/branches")
    public ResponseEntity<List<GithubBranchDTO>> getBranches(
            @PathVariable String owner,
            @PathVariable String repo) {
        Long userId = getAuthenticatedUserId();
        List<GithubBranchDTO> branches = githubService.listBranches(userId, owner, repo);
        return ResponseEntity.ok(branches);
    }

    /**
     * 파일 트리(디렉토리 구조) 조회
     * 
     * @param owner  레포지토리 소유자
     * @param repo   레포지토리 이름
     * @param branch 조회할 브랜치 이름
     * @return 해당 브랜치의 최상위 파일/폴더 목록
     */
    @GetMapping("/repos/{owner}/{repo}/tree")
    public ResponseEntity<List<GithubTreeEntryDTO>> getTree(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam String branch) {
        Long userId = getAuthenticatedUserId();
        List<GithubTreeEntryDTO> tree = githubService.getTree(userId, owner, repo, branch);
        return ResponseEntity.ok(tree);
    }

    /**
     * 파일 내용 조회
     * 
     * @param owner 레포지토리 소유자
     * @param repo  레포지토리 이름
     * @param path  파일 경로
     * @return 파일의 실제 내용(텍스트)
     */
    @GetMapping("/repos/{owner}/{repo}/content")
    public ResponseEntity<GithubFileDTO> getFileContent(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam String path) {
        Long userId = getAuthenticatedUserId();
        GithubFileDTO response = githubService.getFileContent(userId, owner, repo, path);
        return ResponseEntity.ok(response);
    }

    /**
     * GitHub 파일 내용 데이터베이스 저장
     * 
     * 분석을 수행하기 전에, GitHub에서 가져온 원본 코드를 우리 데이터베이스에 먼저 저장합니다.
     * 이는 분석 결과와 원본 코드를 매핑하고, 나중에 이력을 조회할 때 원본 코드를 보여주기 위함입니다.
     * 
     * @param request 저장할 파일 정보 (레포지토리 URL, 경로, 소유자 등)
     * @return 생성된 파일 ID (fileId) - 이 ID는 추후 분석 요청 시 사용됩니다.
     */
    @PostMapping("/save-file")
    public ResponseEntity<ApiResponse<Map<String, String>>> saveFileContent(
            @RequestBody FileSaveRequestDTO request) {

        // 현재 로그인된 사용자 ID를 주입
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
        request.setUserId(userDetails.id());

        String fileId = githubService.saveFileContentToDB(request);

        return ResponseEntity.ok(
                ApiResponse.success(Map.of(
                        "fileId", fileId,
                        "message", "파일 내용이 성공적으로 저장되었습니다.")));
    }

    /**
     * 인증된 사용자 ID 추출 헬퍼 메서드
     * 
     * SecurityContext에서 안전하게 사용자 ID를 꺼내옵니다.
     * 인증 정보가 없거나 올바르지 않으면 null을 반환합니다.
     */
    private Long getAuthenticatedUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof JwtUserDetails) {
                return ((JwtUserDetails) authentication.getPrincipal()).id();
            }
        } catch (Exception e) {
            // 로그 없이 조용히 실패 처리 (필요시 로그 추가)
        }
        return null;
    }
}
