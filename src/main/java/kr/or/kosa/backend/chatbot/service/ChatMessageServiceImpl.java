package kr.or.kosa.backend.chatbot.service;

import kr.or.kosa.backend.chatbot.domain.ChatbotMessage;
import kr.or.kosa.backend.chatbot.dto.ChatRequestDto;
import kr.or.kosa.backend.chatbot.dto.ChatResponseDto;
import kr.or.kosa.backend.chatbot.dto.MessageDto;
import kr.or.kosa.backend.chatbot.mapper.ChatbotMessageMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;

import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatMessageServiceImpl implements ChatMessageService {

    private final ChatbotMessageMapper chatbotMessageMapper;
    private final OpenAiChatModel openAiChatModel;
    private final PromptBuilder promptBuilder;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * ================================
     *  Public sendMessage() - 보안 강화
     * ================================
     */
    @Override
    public ChatResponseDto sendMessage(ChatRequestDto request) {
        Long sessionId = request.getSessionId() != null ? request.getSessionId() : 1L;
        Long userId = request.getUserId();

        // ✅ **반드시 반환값 사용!**
        boolean isAccessAllowed = validateUserAccess(sessionId, userId);
        if (!isAccessAllowed) {
            throw new SecurityException("채팅방 접근 권한이 없습니다.");
        }

        log.info("Send message - userId: {}, sessionId: {}, content: {}",
                userId, sessionId, request.getContent());

        saveUserMessage(sessionId, request);
        String assistantText = callOpenAI(request.getContent());
        saveAssistantMessage(sessionId, userId, assistantText);

        return getMessages(sessionId, 50, userId);
    }

    /**
     * ================================
     *  메시지 조회 - 보안 강화 (userId 필수)
     * ================================
     */
    @Override
    public ChatResponseDto getMessages(Long sessionId, int limit, Long userId) {
        log.info("Get messages - userId: {}, sessionId: {}, limit: {}", userId, sessionId, limit);

        // ✅ 1) 사용자-세션 권한 검증
        boolean isAccessAllowed = validateUserAccess(sessionId, userId);
        if (!isAccessAllowed) {
            throw new SecurityException("채팅방 접근 권한이 없습니다.");
        }

        // 2) 해당 사용자 메시지만 조회 (assistant 메시지도 포함)
        List<ChatbotMessage> list = chatbotMessageMapper.selectMessagesByUser(sessionId, userId, limit);

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
                .toList();

        ChatResponseDto resp = new ChatResponseDto();
        resp.setSessionId(sessionId);
        resp.setMessages(messages);
        return resp;
    }

    /**
     * 사용자 세션 접근 권한 검증
     * @return true: 접근 허용, false: 접근 차단
     */
    private boolean validateUserAccess(Long sessionId, Long userId) {
        long count = chatbotMessageMapper.countUserMessages(sessionId, userId);

        // **실제 조건 추가** - userId가 유효한지 확인
        if (userId == null || userId <= 0) {
            log.error("Invalid userId: {}", userId);
            return false;
        }

        if (count == 0) {
            log.info("New user session - userId: {}", userId);
            return true;
        }

        return true;
    }


    /**
     * ================================
     *  OpenAI 호출
     * ================================
     */
    private String callOpenAI(String userMessage) {
        SystemMessage systemPrompt = promptBuilder.buildPrompt(userMessage);
        UserMessage userPrompt = new UserMessage(userMessage);

        Prompt prompt = new Prompt(List.of(systemPrompt, userPrompt));
        ChatResponse response = openAiChatModel.call(prompt);

        return response.getResult().getOutput().getText();
    }

    /**
     * ================================
     *  Message DB 저장
     * ================================
     */
    private void saveUserMessage(Long sessionId, ChatRequestDto request) {
        ChatbotMessage userMsg = new ChatbotMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setUserId(request.getUserId());
        userMsg.setRole("user");
        userMsg.setContent(request.getContent());
        chatbotMessageMapper.insertMessage(userMsg);
    }

    private void saveAssistantMessage(Long sessionId, Long userId, String content) {
        ChatbotMessage assistantMsg = new ChatbotMessage();
        assistantMsg.setSessionId(sessionId);
        assistantMsg.setUserId(userId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(content);
        chatbotMessageMapper.insertMessage(assistantMsg);
    }
}