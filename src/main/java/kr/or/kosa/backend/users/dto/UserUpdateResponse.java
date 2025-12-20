package kr.or.kosa.backend.users.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 사용자 정보 수정 응답 DTO
 */
@Getter
@Builder
public class UserUpdateResponse {
    private final boolean success;
    private final String message;
    private final UserResponseDto user;
}