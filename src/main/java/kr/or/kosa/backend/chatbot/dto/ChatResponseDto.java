package kr.or.kosa.backend.chatbot.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 챗봇 응답 DTO - 세션 정보 + 메시지 목록
 */
@Data
@Builder
public class ChatResponseDto {
    private Long sessionId;
    private List<MessageDto> messages;
}