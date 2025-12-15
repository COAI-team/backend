package kr.or.kosa.backend.auth.github.service;

import kr.or.kosa.backend.auth.github.dto.GitHubRepoDto;

import java.util.List;

/**
 * GitHub 커밋 서비스 인터페이스
 * 저장소 관리 및 파일 커밋 기능 제공
 *
 * @since 2025-12-13
 */
public interface GitHubCommitService {

    /**
     * 사용자의 GitHub 저장소 목록 조회
     *
     * @param accessToken GitHub 액세스 토큰
     * @return 저장소 목록
     */
    List<GitHubRepoDto> listRepositories(String accessToken);

    /**
     * 새 GitHub 저장소 생성
     *
     * @param accessToken GitHub 액세스 토큰
     * @param repoName 저장소 이름
     * @param description 저장소 설명
     * @param isPrivate 비공개 여부
     * @return 생성된 저장소 정보
     */
    GitHubRepoDto createRepository(String accessToken, String repoName, String description, boolean isPrivate);

    /**
     * 알고리즘 풀이 결과를 GitHub에 커밋
     *
     * @param accessToken GitHub 액세스 토큰
     * @param repoFullName 저장소 전체 이름 (owner/repo)
     * @param problemId 문제 ID
     * @param problemTitle 문제 제목
     * @param difficulty 난이도 (폴더 구조용)
     * @param sourceCode 소스 코드
     * @param languageName 언어 이름 (파일 확장자 결정용)
     * @param readmeContent README.md 내용
     * @return 커밋 URL
     */
    String commitSolution(
            String accessToken,
            String repoFullName,
            Long problemId,
            String problemTitle,
            String difficulty,
            String sourceCode,
            String languageName,
            String readmeContent
    );

    /**
     * README.md 내용 생성
     *
     * @param problemId 문제 ID
     * @param problemTitle 문제 제목
     * @param problemDescription 문제 설명
     * @param difficulty 난이도
     * @param judgeResult 채점 결과
     * @param executionTime 실행 시간 (ms)
     * @param memoryUsage 메모리 사용량 (KB)
     * @param aiFeedback AI 피드백 (nullable)
     * @return README.md 내용
     */
    String generateReadmeContent(
            Long problemId,
            String problemTitle,
            String problemDescription,
            String difficulty,
            String judgeResult,
            Integer executionTime,
            Integer memoryUsage,
            String aiFeedback
    );

    /**
     * 언어 이름으로 파일 확장자 반환
     *
     * @param languageName 언어 이름
     * @return 파일 확장자 (예: "py", "java")
     */
    String getFileExtension(String languageName);
}
