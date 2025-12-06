package kr.or.kosa.backend.auth.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import kr.or.kosa.backend.auth.github.exception.GithubErrorCode;
import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import kr.or.kosa.backend.auth.github.dto.GitHubUserResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubOAuthServiceImpl implements GitHubOAuthService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${github.client-id}")
    private String clientId;

    @Value("${github.client-secret}")
    private String clientSecret;

    /**
     * 🔥 code로 Access Token + 프로필 정보 조회
     */
    @Override
    public GitHubUserResponse getUserInfo(String code) {
        String accessToken = requestAccessToken(code);
        return requestGitHubUser(accessToken);
    }

    /**
     * 🔥 1) 인증 코드(code)로 Access Token 요청
     */
    private String requestAccessToken(String code) {

        String url = "https://github.com/login/oauth/access_token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("code", code);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                JsonNode.class);

        JsonNode responseBody = response.getBody();

        if (responseBody == null) {
            throw new CustomBusinessException(GithubErrorCode.TOKEN_RESPONSE_NULL);
        }

        JsonNode tokenNode = responseBody.get("access_token");

        if (tokenNode == null) {
            throw new CustomBusinessException(GithubErrorCode.TOKEN_MISSING);
        }

        return tokenNode.asText();
    }

    /**
     * 🔥 2) Access Token으로 GitHub 사용자 정보 조회
     */
    private GitHubUserResponse requestGitHubUser(String accessToken) {

        String url = "https://api.github.com/user";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "token " + accessToken);
        headers.set("Accept", "application/json");

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<GitHubUserResponse> response = restTemplate.exchange(url, HttpMethod.GET, entity,
                GitHubUserResponse.class);

        return response.getBody();
    }
}