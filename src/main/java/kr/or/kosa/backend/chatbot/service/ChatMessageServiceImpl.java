package kr.or.kosa.backend.chatbot.service;

import kr.or.kosa.backend.chatbot.domain.ChatbotMessage;
import kr.or.kosa.backend.chatbot.dto.ChatRequestDto;
import kr.or.kosa.backend.chatbot.dto.ChatResponseDto;
import kr.or.kosa.backend.chatbot.dto.MessageDto;
import kr.or.kosa.backend.chatbot.mapper.ChatbotMessageMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.chat.messages.UserMessage;

import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl implements ChatMessageService {

    private final ChatbotMessageMapper chatbotMessageMapper;
    private final OpenAiChatModel openAiChatModel;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public ChatResponseDto sendMessage(ChatRequestDto request) {
        Long sessionId = request.getSessionId() != null ? request.getSessionId() : 1L;

        // 1) User 메시지 저장
        ChatbotMessage userMsg = new ChatbotMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setUserId(request.getUserId());
        userMsg.setRole("user");
        userMsg.setContent(request.getContent());
        chatbotMessageMapper.insertMessage(userMsg);

        // 2) OpenAI 호출
        String assistantText = callOpenAI(request.getContent());

        // 3) assistant 메시지 저장
        ChatbotMessage assistantMsg = new ChatbotMessage();
        assistantMsg.setSessionId(sessionId);
        assistantMsg.setUserId(null);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(assistantText);
        chatbotMessageMapper.insertMessage(assistantMsg);

        // 4) 최신 메시지 반환
        return getMessages(sessionId, 50);
    }

    /**
     * OpenAI 호출
     */
    private String callOpenAI(String userMessage) {

        // 최신 Spring AI 방식 → 옵션 없이 바로 호출
        return openAiChatModel.call(
                new UserMessage(userMessage)
        );
    }

    @Override
    public ChatResponseDto getMessages(Long sessionId, int limit) {
        List<ChatbotMessage> list = chatbotMessageMapper.selectMessages(sessionId, limit);

        List<MessageDto> messages = list.stream()
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .map(m -> {
                    MessageDto dto = new MessageDto();
                    dto.setRole(m.getRole());
                    dto.setContent(m.getContent());
                    dto.setCreatedAt(
                            m.getCreatedAt() != null ? m.getCreatedAt().format(FORMATTER) : null
                    );
                    return dto;
                })
                .toList();   // ← 불변 리스트, 권장 방식

        ChatResponseDto resp = new ChatResponseDto();
        resp.setSessionId(sessionId);
        resp.setMessages(messages);
        return resp;
    }
}