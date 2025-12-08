package kr.or.kosa.backend.chatbot.service;

import kr.or.kosa.backend.chatbot.domain.ChatbotMessage;
import kr.or.kosa.backend.chatbot.dto.ChatRequestDto;
import kr.or.kosa.backend.chatbot.dto.ChatResponseDto;
import kr.or.kosa.backend.chatbot.dto.MessageDto;
import kr.or.kosa.backend.chatbot.mapper.ChatbotMessageMapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl implements ChatMessageService {

    private final ChatbotMessageMapper chatbotMessageMapper;
    // private final OpenAiClient openAiClient;  // 나중에 추가

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public ChatResponseDto sendMessage(ChatRequestDto request) {
        Long sessionId = request.getSessionId() != null ? request.getSessionId() : 1L;

        // 1) user 메시지 저장
        ChatbotMessage userMsg = new ChatbotMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setUserId(request.getUserId());
        userMsg.setRole("user");
        userMsg.setContent(request.getContent());
        chatbotMessageMapper.insertMessage(userMsg);

        // 2) AI 호출해서 assistant 응답 생성 (여기는 임시로 하드코딩)
        String assistantText = "임시 응답입니다. 나중에 OpenAI 연동 부분에 바꾸세요.";

        ChatbotMessage assistantMsg = new ChatbotMessage();
        assistantMsg.setSessionId(sessionId);
        assistantMsg.setUserId(null);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(assistantText);
        chatbotMessageMapper.insertMessage(assistantMsg);

        // 3) 최신 N개 메시지 리턴
        return getMessages(sessionId, 50);
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
                .collect(Collectors.toList());

        ChatResponseDto resp = new ChatResponseDto();
        resp.setSessionId(sessionId);
        resp.setMessages(messages);
        return resp;
    }
}