package kr.or.kosa.backend.codenose.service.agent;

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
import java.util.Collections;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class LangfuseChatModelListener implements ChatModelListener {

    private final LangfuseService langfuseService;

    @Override
    public void onRequest(ChatModelRequestContext context) {
        log.info(">>>>> [LangfuseChatModelListener] onRequest triggered.");
        Instant startTime = Instant.now();
        context.attributes().put("startTime", startTime);

        // Trace Context가 비어있으면 새 Trace 생성
        if (LangfuseContext.getTraceId() == null) {
            String newTraceId = UUID.randomUUID().toString();
            langfuseService.startTrace(newTraceId, "LangChain4j-Auto-Trace", null, Collections.emptyMap());
            context.attributes().put("autoCreatedTrace", true); // 나중에 정리 플래그
            log.info(">>>>> [LangfuseChatModelListener] Auto-created Trace: {}", newTraceId);
        }
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        log.info(">>>>> [LangfuseChatModelListener] onResponse triggered.");
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
        } finally {
            // 자동 생성된 Trace면 종료 처리
            if (Boolean.TRUE.equals(context.attributes().get("autoCreatedTrace"))) {
                LangfuseContext.clean();
                log.info(">>>>> [LangfuseChatModelListener] Auto-created Trace cleaned up.");
            }
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
        } finally {
            // 자동 생성된 Trace면 종료 처리
            if (Boolean.TRUE.equals(context.attributes().get("autoCreatedTrace"))) {
                LangfuseContext.clean();
                log.info(">>>>> [LangfuseChatModelListener] Auto-created Trace cleaned up (on error).");
            }
        }
    }
}
