package kr.or.kosa.backend.auth.github.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 사용자 GitHub 자동커밋 설정 DTO
 * 데이터베이스 테이블: USER_GITHUB_SETTINGS
 *
 * 용도:
 * - 사용자별 GitHub 저장소 설정 저장
 * - 자동 커밋 활성화 여부 관리
 *
 * @since 2025-12-13
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserGithubSettingsDto {

    /**
     * 설정 고유 식별자
     */
    private Long settingId;

    /**
     * 사용자 ID (1:1 관계)
     */
    private Long userId;

    /**
     * 연동된 저장소명 (예: coai-solutions)
     */
    private String githubRepoName;

    /**
     * 저장소 전체 URL (예: https://github.com/user/coai-solutions)
     */
    private String githubRepoUrl;

    /**
     * 자동 커밋 활성화 여부
     * true: 통과 시 자동 커밋
     * false: 수동 버튼으로 커밋
     */
    private Boolean autoCommitEnabled;

    /**
     * 생성 일시
     */
    private LocalDateTime createdAt;

    /**
     * 수정 일시
     */
    private LocalDateTime updatedAt;

    /**
     * 저장소 설정 완료 여부 확인
     * @return 저장소가 설정되어 있으면 true
     */
    public boolean isRepositoryConfigured() {
        return githubRepoName != null && !githubRepoName.isEmpty()
                && githubRepoUrl != null && !githubRepoUrl.isEmpty();
    }
}
