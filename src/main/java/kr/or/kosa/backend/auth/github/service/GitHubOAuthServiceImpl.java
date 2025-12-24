package kr.or.kosa.backend.auth.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import kr.or.kosa.backend.auth.github.dto.GitHubUserResponse;
import kr.or.kosa.backend.auth.github.exception.GithubErrorCode;
import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
public class GitHubOAuthServiceImpl implements GitHubOAuthService {

    private static final String ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_API_PATH = "/user";

    private final WebClient webClient;
    private final String clientId;
    private final String clientSecret;

    public GitHubOAuthServiceImpl(
            WebClient.Builder webClientBuilder,
            @Value("${github.client-id}") String clientId,
            @Value("${github.client-secret}") String clientSecret
    ) {
        this.webClient = webClientBuilder
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

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
        JsonNode responseBody = webClient.post()
                .uri(ACCESS_TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters
                        .fromFormData("client_id", clientId)
                        .with("client_secret", clientSecret)
                        .with("code", code))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

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
        GitHubUserResponse body = webClient.get()
                .uri(USER_API_PATH)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(GitHubUserResponse.class)
                .block();

        if (body == null) {
            throw new CustomBusinessException(GithubErrorCode.TOKEN_RESPONSE_NULL);
        }

        // 이메일이 비공개인 경우 /user/emails API로 조회
        if (body.getEmail() == null || body.getEmail().isBlank()) {
            String primaryEmail = requestPrimaryEmail(accessToken);
            if (primaryEmail != null) {
                return GitHubUserResponse.builder()
                        .id(body.getId())
                        .login(body.getLogin())
                        .email(primaryEmail)
                        .avatarUrl(body.getAvatarUrl())
                        .name(body.getName())
                        .build();
            }
        }

        return body;
    }

    /**
     * 🔥 3) 비공개 이메일 조회 (/user/emails API)
     */
    private String requestPrimaryEmail(String accessToken) {
        try {
            JsonNode emails = webClient.get()
                    .uri("/user/emails")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (emails != null && emails.isArray()) {
                for (JsonNode email : emails) {
                    boolean isPrimary = email.has("primary") && email.get("primary").asBoolean();
                    boolean isVerified = email.has("verified") && email.get("verified").asBoolean();
                    if (isPrimary && isVerified) {
                        return email.get("email").asText();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch user emails: {}", e.getMessage());
        }
        return null;
    }
}