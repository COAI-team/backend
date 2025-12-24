package kr.or.kosa.backend.auth.github.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GitHubUserResponse {

    private Long id;
    private String login;
    private String email;
    private String avatarUrl;
    private String name;   // 있으면 유지
}