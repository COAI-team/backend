package kr.or.kosa.backend.chatbot.dto;

import lombok.Data;
import java.util.List;

@Data
public class ChatResponseDto {
    private Long sessionId;
    private List<MessageDto> messages;
}
