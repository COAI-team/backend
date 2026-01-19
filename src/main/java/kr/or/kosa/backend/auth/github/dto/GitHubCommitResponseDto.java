package kr.or.kosa.backend.auth.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * GitHub 커밋 응답 DTO
 * GitHub Contents API 응답 매핑용
 *
 * @since 2025-12-13
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitHubCommitResponseDto {

    /**
     * 커밋 정보
     */
    private CommitInfo commit;

    /**
     * 컨텐츠 정보
     */
    private ContentInfo content;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommitInfo {
        private String sha;

        @JsonProperty("html_url")
        private String htmlUrl;

        private String message;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContentInfo {
        private String name;
        private String path;
        private String sha;

        @JsonProperty("html_url")
        private String htmlUrl;
    }
}
