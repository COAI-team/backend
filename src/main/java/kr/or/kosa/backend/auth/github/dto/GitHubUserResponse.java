package kr.or.kosa.backend.auth.github.dto;

import lombok.Data;

@Data
public class GitHubUserResponse {

    private Long id;
    private String login;
    private String email;
    private String name;
    private String avatar_url;
}