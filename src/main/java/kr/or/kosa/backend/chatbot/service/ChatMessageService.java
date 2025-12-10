package kr.or.kosa.backend.chatbot.service;

import kr.or.kosa.backend.chatbot.dto.ChatRequestDto;
import kr.or.kosa.backend.chatbot.dto.ChatResponseDto;

public interface ChatMessageService {
    ChatResponseDto sendMessage(ChatRequestDto request);
    ChatResponseDto getMessages(Long sessionId, int limit, Long userId);
}
