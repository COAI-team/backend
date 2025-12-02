package kr.or.kosa.backend.auth.github.service;

import kr.or.kosa.backend.auth.github.dto.GitHubUserResponse;

public interface GitHubOAuthService {
    GitHubUserResponse getUserInfo(String code);
}