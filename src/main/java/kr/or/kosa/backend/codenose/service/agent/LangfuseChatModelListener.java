package kr.or.kosa.backend.codenose.service.agent;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import kr.or.kosa.backend.codenose.service.LangfuseService;
import kr.or.kosa.backend.codenose.service.trace.LangfuseContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listen to LangChain4j events and send generations to Langfuse.
 * Uses ThreadLocal trace context to link generations to the parent trace/span.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LangfuseChatModelListener implements ChatModelListener {

    private final LangfuseService langfuseService;
    private final Map<String, Instant> startTimes = new ConcurrentHashMap<>();

    @Override
    public void onRequest(ChatModelRequestContext context) {
        // 요청 ID 또는 고유 식별자로 시작 시간 저장
        // LangChain4j 컨텍스트의 attributes 맵을 사용할 수 있습니다.
        context.attributes().put("startTime", Instant.now());
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        try {
            Instant startTime = (Instant) context.attributes().get("startTime");
            if (startTime == null)
                startTime = Instant.now();
            Instant endTime = Instant.now();

            String model = context.response().model();
            String content = context.response().aiMessage().text();

            // 토큰 사용량 추출
            int totalTokens = 0;
            if (context.response().tokenUsage() != null) {
                totalTokens = context.response().tokenUsage().totalTokenCount();
            }

            // 입력값: 사용자 메시지 전체 또는 마지막 메시지
            String input = context.request().messages().toString();

            langfuseService.sendGeneration(
                    "LangChain4j Generation",
                    startTime,
                    endTime,
                    model,
                    input,
                    content,
                    totalTokens);
        } catch (Exception e) {
            log.error("Langfuse에 LangChain4j 응답 로깅 실패", e);
        }
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        try {
            Instant startTime = (Instant) context.attributes().get("startTime");
            if (startTime == null)
                startTime = Instant.now();
            Instant endTime = Instant.now();

            langfuseService.sendGeneration(
                    "LangChain4j Error",
                    startTime,
                    endTime,
                    "unknown",
                    context.request().messages().toString(),
                    "Error: " + context.error().getMessage(),
                    0);
        } catch (Exception e) {
            log.error("Langfuse에 LangChain4j 에러 로깅 실패", e);
        }
    }
}
