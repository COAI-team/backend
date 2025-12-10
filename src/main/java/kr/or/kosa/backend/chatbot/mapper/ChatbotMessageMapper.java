package kr.or.kosa.backend.chatbot.mapper;

import kr.or.kosa.backend.chatbot.domain.ChatbotMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatbotMessageMapper {

    void insertMessage(ChatbotMessage message);

    // 기존 메서드 (호환성 유지)
    List<ChatbotMessage> selectMessages(
            @Param("sessionId") Long sessionId,
            @Param("limit") int limit
    );

    List<ChatbotMessage> selectMessagesByUser(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId,
            @Param("limit") int limit
    );

    long countUserMessages(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId
    );
}