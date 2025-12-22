package kr.or.kosa.backend.users.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 이메일 인증 응답 DTO
 */
@Getter
@Builder
public class EmailResponse {
    private final boolean success;
    private final String message;
    private final Long expireAt;
}