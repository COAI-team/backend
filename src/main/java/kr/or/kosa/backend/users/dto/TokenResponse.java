package kr.or.kosa.backend.users.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 토큰 재발급 응답 DTO
 */
@Getter
@Builder
public class TokenResponse {
    private final boolean success;
    private final String accessToken;
}