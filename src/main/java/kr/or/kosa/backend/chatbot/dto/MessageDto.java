package kr.or.kosa.backend.chatbot.dto;

import kr.or.kosa.backend.chatbot.domain.ChatbotMessage;
import lombok.Data;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 챗봇 메시지 응답 DTO
 */
@Data
public class MessageDto {
    private String role;
    private String content;
    private String createdAt;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * ChatbotMessage 리스트를 MessageDto 리스트로 변환 (Static Factory)
     */
    public static List<MessageDto> fromChatbotMessages(List<ChatbotMessage> messages) {
        return messages.stream()
                .map(msg -> {
                    MessageDto dto = new MessageDto();
                    dto.setRole(msg.getRole());
                    dto.setContent(msg.getContent());
                    dto.setCreatedAt(msg.getCreatedAt() != null
                            ? msg.getCreatedAt().format(FORMATTER)
                            : null);
                    return dto;
                })
                .toList();
    }
}