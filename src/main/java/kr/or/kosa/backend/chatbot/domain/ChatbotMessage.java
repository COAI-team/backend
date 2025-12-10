package kr.or.kosa.backend.chatbot.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ChatbotMessage {
    private Long chatbotMessageId;
    private Long sessionId;
    private Long userId;
    private String role;          // "user", "assistant", "system"
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
