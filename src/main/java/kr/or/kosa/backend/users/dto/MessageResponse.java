package kr.or.kosa.backend.users.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 공통 메시지 응답 DTO (성공/실패)
 */
@Getter
@Builder
public class MessageResponse {
    private final boolean success;
    private final String message;
}