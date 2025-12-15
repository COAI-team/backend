package kr.or.kosa.backend.codenose.service;

import kr.or.kosa.backend.codenose.dto.langfuse.LangfuseDto;
import kr.or.kosa.backend.codenose.service.trace.LangfuseContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class LangfuseService {

    private final RestClient restClient;
    private final boolean enabled;

    public LangfuseService(
            @Value("${langfuse.host:http://localhost:3000}") String host,
            @Value("${langfuse.public-key:}") String publicKey,
            @Value("${langfuse.secret-key:}") String secretKey) {

        if (publicKey.isEmpty() || secretKey.isEmpty()) {
            log.warn("Langfuse 자격 증명 미설정. Langfuse 연동 비활성화."); // Translated comment
            this.enabled = false;
            this.restClient = null;
        } else {
            this.enabled = true;
            String auth = publicKey + ":" + secretKey;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

            this.restClient = RestClient.builder()
                    .baseUrl(host)
                    .defaultHeader("Authorization", "Basic " + encodedAuth)
                    .defaultHeader("Content-Type", "application/json")
                    .build();
        }
    }

    /**
     * 새로운 Trace 시작 - LangfuseContext 초기화
     */
    public void startTrace(String id, String name, String userId, Map<String, Object> metadata) {
        if (!enabled)
            return;
        LangfuseContext.setTraceId(id);
        sendEvent(new LangfuseDto.Event(
                UUID.randomUUID().toString(),
                "trace-create",
                Instant.now().toString(),
                new LangfuseDto.TraceBody(id, name, userId, metadata, "1.0.0", "v1"))); // Added version fields
    }

    /**
     * [Deprecated] Trace 시작 (이전 호환성)
     */
    @Deprecated
    public void sendTrace(String id, String name, String userId, Map<String, Object> metadata) {
        startTrace(id, name, userId, metadata);
    }

    /**
     * Span 시작 - LangfuseContext 스택 관리
     */
    public String startSpan(String name, Instant startTime, Map<String, Object> input) {
        if (!enabled)
            return null;

        String traceId = LangfuseContext.getTraceId();
        if (traceId == null) {
            // 트레이스가 없으면 임시 생성
            traceId = UUID.randomUUID().toString();
            LangfuseContext.setTraceId(traceId);
        }

        String parentId = LangfuseContext.getCurrentParentId();
        String spanId = UUID.randomUUID().toString();

        LangfuseContext.pushParentId(spanId); // 스택에 현재 스팬 추가

        sendEvent(new LangfuseDto.Event(
                UUID.randomUUID().toString(),
                "span-create",
                Instant.now().toString(),
                new LangfuseDto.SpanBody(
                        spanId,
                        traceId,
                        parentId,
                        name,
                        startTime.toString(),
                        null,
                        input,
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        "DEFAULT", null)));
        return spanId;
    }

    /**
     * Span 종료 - LangfuseContext 스택 관리
     */
    public void endSpan(String spanId, Instant endTime, Map<String, Object> output) {
        if (!enabled)
            return;

        // spanId가 null이면 현재 컨텍스트의 최상위 스팬 종료
        LangfuseContext.popParentId();

        // 실제로는 span-update 이벤트를 보내거나 해야 하지만, Fire-and-forget 방식의 현재 구현에서는
        // startSpan 시점에 create 이벤트를 보냈으므로 여기서는 Context 정리만 수행합니다.
        // 추후 정확한 지속시간 기록이 필요하면 span-update 구현이 필요합니다.
    }

    /**
     * Generation 전송 (Context 인식)
     */
    public void sendGeneration(String name, Instant startTime, Instant endTime, String model,
            Object input, Object output, int totalTokens) {
        if (!enabled)
            return;

        String traceId = LangfuseContext.getTraceId();
        String parentId = LangfuseContext.getCurrentParentId();

        sendGeneration(UUID.randomUUID().toString(), traceId, parentId, name, startTime, endTime, model, input, output,
                totalTokens);
    }

    /**
     * Legacy Send Generation (ID 명시적 전달)
     */
    public void sendGeneration(String id, String traceId, String parentObservationId, String name,
            Instant startTime, Instant endTime, String model,
            Object input, Object output,
            int totalTokens) {
        if (!enabled)
            return;

        LangfuseDto.Usage usage = new LangfuseDto.Usage(0, 0, totalTokens, "TOKENS");

        LangfuseDto.GenerationBody body = new LangfuseDto.GenerationBody(
                id, traceId, parentObservationId, name,
                startTime.toString(), endTime != null ? endTime.toString() : null,
                model, Collections.emptyMap(),
                input, output, Collections.emptyMap(),
                "DEFAULT", null, usage);
        sendEvent(new LangfuseDto.Event(UUID.randomUUID().toString(), "generation-create", Instant.now().toString(),
                body));
    }

    /**
     * Span 수동 전송 (컨텍스트 기반) - 원자적 실행
     */
    public void sendSpan(String name, Instant startTime, Instant endTime,
            Map<String, Object> input, Map<String, Object> output, Map<String, Object> metadata) {
        if (!enabled)
            return;

        String traceId = LangfuseContext.getTraceId();
        String parentId = LangfuseContext.getCurrentParentId();
        String spanId = UUID.randomUUID().toString();

        LangfuseDto.SpanBody body = new LangfuseDto.SpanBody(
                spanId, traceId, parentId, name,
                startTime.toString(), endTime != null ? endTime.toString() : null,
                input, output, metadata,
                "DEFAULT", null);
        sendEvent(new LangfuseDto.Event(UUID.randomUUID().toString(), "span-create", Instant.now().toString(), body));
    }

    /**
     * Span 수동 전송 (ID 명시) - Legacy
     */
    public void sendSpan(String id, String traceId, String parentObservationId, String name,
            Instant startTime, Instant endTime,
            Map<String, Object> input, Map<String, Object> output, Map<String, Object> metadata) {
        if (!enabled)
            return;

        LangfuseDto.SpanBody body = new LangfuseDto.SpanBody(
                id, traceId, parentObservationId, name,
                startTime.toString(), endTime != null ? endTime.toString() : null,
                input, output, metadata,
                "DEFAULT", null);
        sendEvent(new LangfuseDto.Event(UUID.randomUUID().toString(), "span-create", Instant.now().toString(), body));
    }

    private void sendEvent(LangfuseDto.Event event) {
        try {
            LangfuseDto.BatchRequest batch = new LangfuseDto.BatchRequest(Collections.singletonList(event));

            restClient.post()
                    .uri("/api/public/ingestion")
                    .body(batch)
                    .retrieve()
                    .toBodilessEntity();

        } catch (Exception e) {
            log.error("Langfuse 이벤트 전송 실패: {}", e.getMessage()); // Translated comment
        }
    }
}
