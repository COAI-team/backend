package kr.or.kosa.backend.auth.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * GitHub 저장소 정보 DTO
 * GitHub API 응답 매핑용
 *
 * @since 2025-12-13
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitHubRepoDto {

    /**
     * 저장소 ID
     */
    private Long id;

    /**
     * 저장소 이름
     */
    private String name;

    /**
     * 저장소 전체 이름 (owner/repo)
     */
    @JsonProperty("full_name")
    private String fullName;

    /**
     * 저장소 설명
     */
    private String description;

    /**
     * 비공개 여부
     */
    @JsonProperty("private")
    private Boolean isPrivate;

    /**
     * 저장소 HTML URL
     */
    @JsonProperty("html_url")
    private String htmlUrl;

    /**
     * 저장소 API URL
     */
    private String url;

    /**
     * 기본 브랜치명
     */
    @JsonProperty("default_branch")
    private String defaultBranch;
}
