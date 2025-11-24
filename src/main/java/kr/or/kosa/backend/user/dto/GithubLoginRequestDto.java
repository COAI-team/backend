package kr.or.kosa.backend.user.dto;

import lombok.Data;

@Data
public class GithubLoginRequestDto {

    // GitHub OAuth Access Token
    private String githubAccessToken;

    // GitHub 유저 고유 ID (필수)
    private String githubUserId;

    // 아래 값들은 GitHub API로부터 받아오는 정보 (있으면 활용)
    private String email;
    private String name;
    private String image;
}