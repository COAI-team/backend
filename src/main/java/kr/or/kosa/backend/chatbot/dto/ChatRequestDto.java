package kr.or.kosa.backend.chatbot.dto;

import lombok.Data;

@Data
public class ChatRequestDto {
    private Long sessionId;   // 단일 세션이면 생략 가능
    private Long userId;
    private String content;
}