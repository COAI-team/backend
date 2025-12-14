package kr.or.kosa.backend.auth.github.service;

import kr.or.kosa.backend.algorithm.dto.AlgoProblemDto;
import kr.or.kosa.backend.algorithm.dto.AlgoSubmissionDto;
import kr.or.kosa.backend.algorithm.mapper.AlgorithmProblemMapper;
import kr.or.kosa.backend.algorithm.mapper.AlgorithmSubmissionMapper;
import kr.or.kosa.backend.algorithm.mapper.LanguageMapper;
import kr.or.kosa.backend.auth.github.dto.GitHubRepoDto;
import kr.or.kosa.backend.auth.github.dto.UserGithubSettingsDto;
import kr.or.kosa.backend.auth.github.exception.GithubErrorCode;
import kr.or.kosa.backend.auth.github.mapper.UserGithubSettingsMapper;
import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import kr.or.kosa.backend.commons.util.EncryptionUtil;
import kr.or.kosa.backend.users.domain.Users;
import kr.or.kosa.backend.users.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 사용자 GitHub 설정 서비스
 * GitHub 저장소 설정 및 커밋 기능 관리
 *
 * @since 2025-12-13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserGithubSettingsService {

    private final UserGithubSettingsMapper settingsMapper;
    private final UserMapper userMapper;
    private final AlgorithmSubmissionMapper submissionMapper;
    private final AlgorithmProblemMapper problemMapper;
    private final LanguageMapper languageMapper;
    private final GitHubCommitService gitHubCommitService;
    private final EncryptionUtil encryptionUtil;

    /**
     * 사용자의 GitHub 설정 조회
     */
    public UserGithubSettingsDto getSettings(Long userId) {
        UserGithubSettingsDto settings = settingsMapper.selectByUserId(userId);

        if (settings == null) {
            // 설정이 없으면 기본값 반환
            return UserGithubSettingsDto.builder()
                    .userId(userId)
                    .autoCommitEnabled(false)
                    .build();
        }

        return settings;
    }

    /**
     * 사용자의 GitHub 설정 저장 (UPSERT)
     */
    @Transactional
    public UserGithubSettingsDto saveSettings(Long userId, UserGithubSettingsDto settings) {
        settings.setUserId(userId);

        UserGithubSettingsDto existing = settingsMapper.selectByUserId(userId);

        if (existing == null) {
            settingsMapper.insert(settings);
        } else {
            settingsMapper.update(settings);
        }

        return settingsMapper.selectByUserId(userId);
    }

    /**
     * 자동 커밋 설정 토글
     */
    @Transactional
    public boolean toggleAutoCommit(Long userId, boolean enabled) {
        UserGithubSettingsDto existing = settingsMapper.selectByUserId(userId);

        if (existing == null) {
            // 설정이 없으면 새로 생성
            UserGithubSettingsDto newSettings = UserGithubSettingsDto.builder()
                    .userId(userId)
                    .autoCommitEnabled(enabled)
                    .build();
            settingsMapper.insert(newSettings);
        } else {
            settingsMapper.updateAutoCommitEnabled(userId, enabled);
        }

        return enabled;
    }

    /**
     * 사용자의 GitHub 저장소 목록 조회
     */
    public List<GitHubRepoDto> listRepositories(Long userId) {
        String accessToken = getDecryptedGitHubToken(userId);
        return gitHubCommitService.listRepositories(accessToken);
    }

    /**
     * 새 GitHub 저장소 생성
     */
    @Transactional
    public GitHubRepoDto createRepository(Long userId, String repoName, boolean isPrivate) {
        String accessToken = getDecryptedGitHubToken(userId);

        GitHubRepoDto repo = gitHubCommitService.createRepository(
                accessToken,
                repoName,
                "CoAI 알고리즘 풀이 저장소",
                isPrivate
        );

        // 생성된 저장소를 설정에 저장
        if (repo != null) {
            UserGithubSettingsDto settings = getSettings(userId);
            settings.setGithubRepoName(repo.getName());
            settings.setGithubRepoUrl(repo.getHtmlUrl());
            saveSettings(userId, settings);
        }

        return repo;
    }

    /**
     * 저장소 설정 업데이트
     */
    @Transactional
    public void updateRepository(Long userId, String repoName, String repoUrl) {
        UserGithubSettingsDto existing = settingsMapper.selectByUserId(userId);

        if (existing == null) {
            UserGithubSettingsDto newSettings = UserGithubSettingsDto.builder()
                    .userId(userId)
                    .githubRepoName(repoName)
                    .githubRepoUrl(repoUrl)
                    .autoCommitEnabled(false)
                    .build();
            settingsMapper.insert(newSettings);
        } else {
            settingsMapper.updateRepository(userId, repoName, repoUrl);
        }
    }

    /**
     * 제출 결과를 GitHub에 커밋
     */
    @Transactional
    public String commitSubmission(Long userId, Long submissionId) {
        // 1. 제출 정보 조회
        AlgoSubmissionDto submission = submissionMapper.selectSubmissionById(submissionId);

        if (submission == null) {
            throw new CustomBusinessException(GithubErrorCode.GITHUB_COMMIT_FAILED);
        }

        // 2. 이미 커밋된 경우 체크
        if (submission.getGithubCommitUrl() != null) {
            throw new CustomBusinessException(GithubErrorCode.GITHUB_ALREADY_COMMITTED);
        }

        // 3. 본인 제출인지 확인
        if (!submission.getUserId().equals(userId)) {
            throw new CustomBusinessException(GithubErrorCode.GITHUB_COMMIT_FAILED);
        }

        // 4. GitHub 설정 확인
        UserGithubSettingsDto settings = settingsMapper.selectByUserId(userId);
        if (settings == null || !settings.isRepositoryConfigured()) {
            throw new CustomBusinessException(GithubErrorCode.GITHUB_REPO_NOT_CONFIGURED);
        }

        // 5. 문제 정보 조회
        AlgoProblemDto problem = problemMapper.selectProblemById(submission.getAlgoProblemId());

        // 6. 언어 정보로 파일 확장자 결정
        String languageName = getLanguageName(submission.getLanguageId());

        // 7. README 생성
        String readmeContent = gitHubCommitService.generateReadmeContent(
                problem.getAlgoProblemId(),
                problem.getAlgoProblemTitle(),
                problem.getAlgoProblemDescription(),
                problem.getAlgoProblemDifficulty() != null ? problem.getAlgoProblemDifficulty().name() : null,
                submission.getJudgeResult() != null ? submission.getJudgeResult().name() : null,
                submission.getExecutionTime(),
                submission.getMemoryUsage(),
                submission.getAiFeedback()
        );

        // 8. GitHub 액세스 토큰 조회
        String accessToken = getDecryptedGitHubToken(userId);

        // 9. 저장소 full name 추출 (URL에서)
        String repoFullName = extractRepoFullName(settings.getGithubRepoUrl());

        // 10. 난이도 추출
        String difficulty = problem.getAlgoProblemDifficulty() != null
                ? problem.getAlgoProblemDifficulty().name()
                : null;

        // 11. 커밋 실행
        String commitUrl = gitHubCommitService.commitSolution(
                accessToken,
                repoFullName,
                problem.getAlgoProblemId(),
                problem.getAlgoProblemTitle(),
                difficulty,
                submission.getSourceCode(),
                languageName,
                readmeContent
        );

        // 12. 커밋 URL 저장
        if (commitUrl != null) {
            submissionMapper.updateGithubCommitUrl(submissionId, commitUrl);
        }

        return commitUrl;
    }

    /**
     * 사용자의 복호화된 GitHub 토큰 조회
     */
    private String getDecryptedGitHubToken(Long userId) {
        Users user = userMapper.findById(userId);

        if (user == null || user.getGithubToken() == null || user.getGithubToken().isEmpty()) {
            throw new CustomBusinessException(GithubErrorCode.GITHUB_NOT_CONNECTED);
        }

        try {
            return encryptionUtil.decrypt(user.getGithubToken());
        } catch (Exception e) {
            log.error("GitHub 토큰 복호화 실패: {}", e.getMessage());
            throw new CustomBusinessException(GithubErrorCode.GITHUB_NOT_CONNECTED);
        }
    }

    /**
     * 언어 ID로 언어 이름 조회
     */
    private String getLanguageName(Integer languageId) {
        if (languageId == null) {
            return "txt";
        }

        var language = languageMapper.selectById(languageId);
        return language != null ? language.getLanguageName() : "txt";
    }

    /**
     * GitHub URL에서 owner/repo 형식 추출
     */
    private String extractRepoFullName(String repoUrl) {
        if (repoUrl == null) {
            throw new CustomBusinessException(GithubErrorCode.GITHUB_REPO_NOT_CONFIGURED);
        }

        // https://github.com/owner/repo -> owner/repo
        String cleaned = repoUrl
                .replace("https://github.com/", "")
                .replace("http://github.com/", "");

        // 끝에 .git이 있으면 제거
        if (cleaned.endsWith(".git")) {
            cleaned = cleaned.substring(0, cleaned.length() - 4);
        }

        // 끝에 /가 있으면 제거
        if (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }

        return cleaned;
    }
}
