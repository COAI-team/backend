package kr.or.kosa.backend.auth.github.service;

import kr.or.kosa.backend.auth.github.dto.GitHubUserResponse;

public interface GitHubOAuthService {
    /**
     * code로 사용자 Access Token & Profile 조회
     */
    GitHubUserResponse getUserInfo(String code);
}