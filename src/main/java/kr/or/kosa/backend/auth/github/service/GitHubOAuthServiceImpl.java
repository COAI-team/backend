package kr.or.kosa.backend.auth.github.service;

import com.fasterxml.jackson.databind.JsonNode;
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

    @Value("${github.redirect-uri}")
    private String redirectUri;

    @Override
    public GitHubUserResponse getUserInfo(String code) {

        String accessToken = requestAccessToken(code);

        return requestGitHubUser(accessToken);
    }

    private String requestAccessToken(String code) {

        String url = "https://github.com/login/oauth/access_token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        // ✔ GitHub OAuth는 반드시 Form 형태여야 함
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("code", code);
        body.add("redirect_uri", redirectUri);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                JsonNode.class
        );

        log.info("GitHub token response = {}", response.getBody());

        return response.getBody().get("access_token").asText();
    }

    private GitHubUserResponse requestGitHubUser(String accessToken) {

        String url = "https://api.github.com/user";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("Accept", "application/json");

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<GitHubUserResponse> response =
                restTemplate.exchange(url, HttpMethod.GET, entity, GitHubUserResponse.class);

        return response.getBody();
    }
}