package kr.or.kosa.backend.codenose.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    public LangfuseService(
            @Value("${langfuse.host:http://localhost:3000}") String host,
            @Value("${langfuse.public-key:}") String publicKey,
            @Value("${langfuse.secret-key:}") String secretKey,
            ObjectMapper objectMapper) {

        this.objectMapper = objectMapper;

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

        LangfuseContext.pushParentSpan(new LangfuseContext.SpanInfo(spanId, name, startTime.toString(), input));

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
    /**
     * Span 종료 - LangfuseContext 스택 관리 및 결과 전송
     */
    public void endSpan(String spanId, Instant endTime, Map<String, Object> output) {
        if (!enabled)
            return;

        // spanId가 null이면 현재 컨텍스트의 최상위 스팬 ID를 가져옵니다.
        String currentSpanId = (spanId != null) ? spanId : LangfuseContext.getCurrentParentId();

        // 스택에서 제거 및 메타데이터 복원
        LangfuseContext.SpanInfo spanInfo = LangfuseContext.popParentSpan();

        if (currentSpanId == null || spanInfo == null)
            return;

        String traceId = LangfuseContext.getTraceId();

        // Upsert를 위해 startSpan과 동일한 정보를 최대한 포함하여 전송
        LangfuseDto.SpanBody body = new LangfuseDto.SpanBody(
                currentSpanId,
                traceId,
                null,
                spanInfo.name(), // 복원된 Name
                spanInfo.startTime(), // 복원된 StartTime
                endTime.toString(),
                (Map<String, Object>) spanInfo.input(), // 복원된 Input (Map 캐스팅 필요)
                output,
                Collections.emptyMap(),
                "DEFAULT", null);

        sendEvent(new LangfuseDto.Event(
                UUID.randomUUID().toString(),
                "span-create",
                Instant.now().toString(),
                body));

        // 스택이 비었으면 컨텍스트 정리 (Root Span 종료 시 Trace Context 정리)
        // 주의: 비동기 처리가 중첩된 경우 메인 스레드에서 먼저 정리될 위험이 있으나,
        // 현재 구조상 동기식 호출이므로 이 방식이 리소스 누수를 방지함.
        if (!LangfuseContext.hasParent()) {
            LangfuseContext.clean();
        }
    }

    /**
     * Generation 전송 (Context 인식)
     */
    public void sendGeneration(String name, Instant startTime, Instant endTime, String model,
            Object input, Object output, int totalTokens) {
        log.info(">>>>> [sendGeneration] Called. enabled={}", enabled);
        if (!enabled)
            return;

        String traceId = LangfuseContext.getTraceId();
        String parentId = LangfuseContext.getCurrentParentId();
        log.info(">>>>> [sendGeneration] traceId={}, parentId={}", traceId, parentId);

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
        log.info(">>>>> [sendGeneration Legacy] id={}, traceId={}, parentId={}", id, traceId, parentObservationId);
        if (!enabled)
            return;

        LangfuseDto.Usage usage = new LangfuseDto.Usage(0, 0, totalTokens, "TOKENS");

        LangfuseDto.GenerationBody body = new LangfuseDto.GenerationBody(
                id, traceId, parentObservationId, name,
                startTime.toString(), endTime != null ? endTime.toString() : null,
                model, Collections.emptyMap(),
                input, output, Collections.emptyMap(),
                "DEFAULT", null, usage);
        log.info(">>>>> [sendGeneration Legacy] About to send generation-create event.");
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
            log.info(">>>> [Langfuse JSON] Payload: {}", objectMapper.writeValueAsString(event));

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
