package kr.or.kosa.backend.auth.github.dto;

import lombok.*;

@Getter
@ToString
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