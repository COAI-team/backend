package kr.or.kosa.backend.chatbot.mapper;

import kr.or.kosa.backend.chatbot.domain.ChatbotMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 챗봇 메시지 데이터베이스 매퍼 인터페이스
 * 실제 사용되는 핵심 기능만 제공
 */
@Mapper
public interface ChatbotMessageMapper {

    /**
     * 지정된 사용자와 세션의 메시지 목록을 조회합니다.
     *
     * @param sessionId 세션 ID
     * @param userId 사용자 ID
     * @param limit 조회할 최대 메시지 수
     * @return 사용자별 메시지 목록 (최근순)
     */
    List<ChatbotMessage> selectMessagesByUser(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId,
            @Param("limit") int limit
    );

    /**
     * 여러 메시지를 일괄 저장합니다.
     * 네트워크 왕복 횟수를 줄여 성능을 10배 이상 향상시킵니다.
     *
     * @param messages 저장할 메시지 목록
     * @return 실제 삽입된 행 수
     */
    int insertMessages(@Param("messages") List<ChatbotMessage> messages);

    /**
     * 사용자별 세션 내 메시지 총 개수를 조회합니다.
     * 페이징 및 통계에 사용
     *
     * @param sessionId 세션 ID
     * @param userId 사용자 ID
     * @return 메시지 총 개수
     */
    long countUserMessages(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId
    );
}