package kr.or.kosa.backend.chatbot.exception;

import kr.or.kosa.backend.commons.exception.custom.ErrorCode;

/**
 * 챗봇 관련 비즈니스 예외 코드
 * 사용자 인증, 메시지 처리, OpenAI 연동 오류 정의
 */
public enum ChatbotErrorCode implements ErrorCode {

    INVALID_USER_ID("CHT001", "유효하지 않은 사용자 ID입니다."),
    EMPTY_MESSAGE_CONTENT("CHT002", "메시지 내용이 비어있습니다.");

    private final String code;
    private final String message;

    ChatbotErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
