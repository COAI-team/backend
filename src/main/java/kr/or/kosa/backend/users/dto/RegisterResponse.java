package kr.or.kosa.backend.users.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 회원가입 응답 DTO
 */
@Getter
@Builder
public class RegisterResponse {
    private final boolean success;
    private final String message;
    private final Long userId;
}