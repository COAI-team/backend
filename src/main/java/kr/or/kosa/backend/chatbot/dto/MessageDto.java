package kr.or.kosa.backend.chatbot.dto;

import lombok.Data;

@Data
public class MessageDto {
    private String role;
    private String content;
    private String createdAt;
}