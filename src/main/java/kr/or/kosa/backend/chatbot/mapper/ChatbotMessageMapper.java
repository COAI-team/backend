package kr.or.kosa.backend.chatbot.mapper;

import kr.or.kosa.backend.chatbot.domain.ChatbotMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatbotMessageMapper {

    void insertMessage(ChatbotMessage message);

    List<ChatbotMessage> selectMessages(
            @Param("sessionId") Long sessionId,
            @Param("limit") int limit
    );
}