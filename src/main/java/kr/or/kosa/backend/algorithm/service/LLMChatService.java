package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.dto.LLMResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

/**
 * LLM 채팅 서비스 추상화 래퍼
 * OpenAI ChatClient를 감싸서 일관된 인터페이스 제공
 */
@Slf4j
@Service
public class LLMChatService {

    private final ChatClient chatClient;

    public LLMChatService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 단순 텍스트 생성
     *
     * @param userPrompt 사용자 프롬프트
     * @return 생성된 텍스트
     */
    public String generate(String userPrompt) {
        return generate(null, userPrompt);
    }

    /**
     * 시스템 프롬프트와 사용자 프롬프트로 텍스트 생성
     *
     * @param systemPrompt 시스템 프롬프트 (null 가능)
     * @param userPrompt   사용자 프롬프트
     * @return 생성된 텍스트
     */
    public String generate(String systemPrompt, String userPrompt) {
        log.debug("LLM 호출 시작 - systemPrompt 길이: {}, userPrompt 길이: {}",
                systemPrompt != null ? systemPrompt.length() : 0,
                userPrompt.length());

        long startTime = System.currentTimeMillis();

        try {
            ChatClient.ChatClientRequestSpec request = chatClient.prompt();

            if (systemPrompt != null && !systemPrompt.isBlank()) {
                request = request.system(systemPrompt);
            }

            String response = request
                    .user(userPrompt)
                    .call()
                    .content();

            long elapsed = System.currentTimeMillis() - startTime;
            log.debug("LLM 호출 완료 - 응답 길이: {}, 소요시간: {}ms", response.length(), elapsed);

            return response;

        } catch (Exception e) {
            log.error("LLM 호출 실패", e);
            throw new RuntimeException("LLM 호출 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * 텍스트 생성 및 토큰 정보 포함 응답 반환
     *
     * @param systemPrompt 시스템 프롬프트 (null 가능)
     * @param userPrompt   사용자 프롬프트
     * @return LLM 응답 DTO (토큰 정보 포함)
     */
    public LLMResponseDto generateWithMetadata(String systemPrompt, String userPrompt) {
        log.debug("LLM 호출 (메타데이터 포함) - systemPrompt 길이: {}, userPrompt 길이: {}",
                systemPrompt != null ? systemPrompt.length() : 0,
                userPrompt.length());

        long startTime = System.currentTimeMillis();

        try {
            ChatClient.ChatClientRequestSpec request = chatClient.prompt();

            if (systemPrompt != null && !systemPrompt.isBlank()) {
                request = request.system(systemPrompt);
            }

            ChatResponse chatResponse = request
                    .user(userPrompt)
                    .call()
                    .chatResponse();

            long elapsed = System.currentTimeMillis() - startTime;

            String content = chatResponse.getResult().getOutput().getText();
            var usage = chatResponse.getMetadata().getUsage();

            Integer inputTokens = null;
            Integer outputTokens = null;
            Integer totalTokens = null;

            if (usage != null) {
                inputTokens = (int) usage.getPromptTokens();
                outputTokens = (int) usage.getCompletionTokens();
                totalTokens = (int) usage.getTotalTokens();
            }

            String model = chatResponse.getMetadata().getModel();

            log.info("LLM 호출 완료 - 모델: {}, 입력토큰: {}, 출력토큰: {}, 소요시간: {}ms",
                    model, inputTokens, outputTokens, elapsed);

            return LLMResponseDto.builder()
                    .content(content)
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .totalTokens(totalTokens)
                    .responseTimeMs(elapsed)
                    .model(model)
                    .build();

        } catch (Exception e) {
            log.error("LLM 호출 실패 (메타데이터)", e);
            throw new RuntimeException("LLM 호출 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * 구조화된 응답 생성 (JSON -> 객체 변환)
     *
     * @param systemPrompt 시스템 프롬프트 (null 가능)
     * @param userPrompt   사용자 프롬프트
     * @param responseType 응답 클래스 타입
     * @param <T>          응답 타입
     * @return 파싱된 응답 객체
     */
    public <T> T generateAndParse(String systemPrompt, String userPrompt, Class<T> responseType) {
        log.debug("LLM 구조화 응답 요청 - 응답 타입: {}", responseType.getSimpleName());

        long startTime = System.currentTimeMillis();

        try {
            ChatClient.ChatClientRequestSpec request = chatClient.prompt();

            if (systemPrompt != null && !systemPrompt.isBlank()) {
                request = request.system(systemPrompt);
            }

            T response = request
                    .user(userPrompt)
                    .call()
                    .entity(responseType);

            long elapsed = System.currentTimeMillis() - startTime;
            log.debug("LLM 구조화 응답 완료 - 소요시간: {}ms", elapsed);

            return response;

        } catch (Exception e) {
            log.error("LLM 구조화 응답 실패", e);
            throw new RuntimeException("LLM 구조화 응답 중 오류 발생: " + e.getMessage(), e);
        }
    }
    /**
     * Tutor 등 단순 텍스트 응답용 메서드.
     *
     * @param systemPrompt 시스템 프롬프트
     * @param userPrompt   사용자 프롬프트
     * @return LLM의 텍스트 응답
     */
    public String callPlain(String systemPrompt, String userPrompt) {
        long startTime = System.currentTimeMillis();
        try {
            ChatResponse response = chatClient
                    .prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .chatResponse();

            String content = response.getResult().getOutput().getText();
            log.debug("LLM plain 응답 완료 - {}ms", System.currentTimeMillis() - startTime);
            return content;
        } catch (Exception e) {
            log.error("LLM plain 응답 실패", e);
            throw new RuntimeException("LLM plain 응답 중 오류 발생: " + e.getMessage(), e);
        }
    }
}
