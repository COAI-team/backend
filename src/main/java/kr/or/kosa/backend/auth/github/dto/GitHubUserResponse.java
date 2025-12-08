package kr.or.kosa.backend.auth.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GitHubUserResponse {

    private Long id;

    private String login;

    private String name;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    private String email;
}