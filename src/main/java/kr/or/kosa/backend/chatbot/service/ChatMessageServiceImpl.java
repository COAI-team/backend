package kr.or.kosa.backend.chatbot.service;

import kr.or.kosa.backend.chatbot.domain.ChatbotMessage;
import kr.or.kosa.backend.chatbot.dto.ChatRequestDto;
import kr.or.kosa.backend.chatbot.dto.ChatResponseDto;
import kr.or.kosa.backend.chatbot.dto.MessageDto;
import kr.or.kosa.backend.chatbot.exception.ChatbotErrorCode;
import kr.or.kosa.backend.chatbot.mapper.ChatbotMessageMapper;

import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 챗봇 메시지 서비스 구현체
 * OpenAI 통합 + 사용자별 세션 관리 + 보안 검증
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl implements ChatMessageService {

    private static final int DEFAULT_MESSAGE_LIMIT = 50;
    private static final Long DEFAULT_SESSION_ID = 1L;

    private final ChatbotMessageMapper chatbotMessageMapper;
    private final OpenAiChatModel openAiChatModel;
    private final PromptBuilder promptBuilder;

    /**
     * 채팅 메시지 전송 - 사용자 메시지 저장 → AI 응답 → 배치 저장 → 최근 메시지 반환
     *
     * @param request 채팅 요청 (sessionId, userId, content 필수)
     * @return 최신 메시지 목록 포함 응답
     * @throws CustomBusinessException 권한 없음/입력 오류 시
     */
    @Override
    public ChatResponseDto sendMessage(ChatRequestDto request) {
        validateRequest(request);

        Long sessionId = getOrDefaultSessionId(request.getSessionId());
        Long userId = request.getUserId();

        log.info("Send message - userId: {}, sessionId: {}, content: {}",
                userId, sessionId, request.getContent());

        // 1. 사용자 메시지 + AI 응답 생성
        ChatbotMessage userMessage = createUserMessage(sessionId, userId, request.getContent());
        String aiResponse = callOpenAI(request.getContent());
        ChatbotMessage assistantMessage = createAssistantMessage(sessionId, userId, aiResponse);

        // 2. 배치 저장 (성능 최적화)
        int savedCount = chatbotMessageMapper.insertMessages(List.of(userMessage, assistantMessage));
        log.debug("배치 저장 완료: {}개 메시지", savedCount);

        // 3. 최근 메시지 반환
        return getMessages(sessionId, DEFAULT_MESSAGE_LIMIT, userId);
    }

    /**
     * 사용자별 세션 메시지 조회 (보안 검증 포함)
     *
     * @param sessionId 세션 ID
     * @param limit 최대 메시지 수 (최대 100)
     * @param userId 사용자 ID (필수)
     * @return 메시지 목록 응답
     * @throws CustomBusinessException 권한 없음 시
     */
    @Override
    public ChatResponseDto getMessages(Long sessionId, int limit, Long userId) {
        validateUserAccess(sessionId, userId);
        limit = Math.min(limit, 100); // 최대 100개 제한

        log.info("Get messages - userId: {}, sessionId: {}, limit: {}", userId, sessionId, limit);

        // 사용자 메시지만 조회 (assistant 포함)
        List<ChatbotMessage> messages = chatbotMessageMapper.selectMessagesByUser(sessionId, userId, limit);
        List<MessageDto> messageDtos = MessageDto.fromChatbotMessages(messages);

        return ChatResponseDto.builder()
                .sessionId(sessionId)
                .messages(messageDtos)
                .build();
    }

    /**
     * 요청 데이터 유효성 검사
     */
    private void validateRequest(ChatRequestDto request) {
        if (request.getUserId() == null || request.getUserId() <= 0) {
            throw new CustomBusinessException(ChatbotErrorCode.INVALID_USER_ID);
        }
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new CustomBusinessException(ChatbotErrorCode.EMPTY_MESSAGE_CONTENT);
        }
    }

    /**
     * 세션 ID 기본값 처리
     */
    private Long getOrDefaultSessionId(Long sessionId) {
        return sessionId != null ? sessionId : DEFAULT_SESSION_ID;
    }

    /**
     * 사용자 메시지 엔티티 생성
     */
    private ChatbotMessage createUserMessage(Long sessionId, Long userId, String content) {
        ChatbotMessage message = new ChatbotMessage();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole("user");
        message.setContent(content.trim());
        return message;
    }

    /**
     * AI 응답 메시지 엔티티 생성
     */
    private ChatbotMessage createAssistantMessage(Long sessionId, Long userId, String content) {
        ChatbotMessage message = new ChatbotMessage();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole("assistant");
        message.setContent(content);
        return message;
    }

    /**
     * 사용자 세션 접근 권한 검증 (신규 세션 허용)
     *
     * @throws CustomBusinessException 권한 없음 시
     */
    private void validateUserAccess(Long sessionId, Long userId) {
        if (userId == null || userId <= 0) {
            throw new CustomBusinessException(ChatbotErrorCode.INVALID_USER_ID);
        }

        // 신규 세션 허용 (count == 0)
        long messageCount = chatbotMessageMapper.countUserMessages(sessionId, userId);
        log.debug("User session validation - userId: {}, sessionId: {}, count: {}",
                userId, sessionId, messageCount);
    }

    /**
     * OpenAI API 호출 - 프로젝트 컨텍스트 기반 프롬프트 생성
     */
    private String callOpenAI(String userMessage) {
        String fullPrompt = promptBuilder.buildCompleteGuidePrompt(
                "KOSA 백엔드 프로젝트",
                "MAIN",
                userMessage
        );

        UserMessage userPrompt = new UserMessage(fullPrompt);
        ChatResponse response = openAiChatModel.call(new Prompt(List.of(userPrompt)));
        return response.getResult().getOutput().getText();
    }
}