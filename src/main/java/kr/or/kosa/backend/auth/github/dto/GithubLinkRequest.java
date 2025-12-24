package kr.or.kosa.backend.auth.github.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GithubLinkRequest {
    private Long id;        // github user id
    private String login;   // github login
    private String email;   // nullable
    private String avatarUrl;
}
