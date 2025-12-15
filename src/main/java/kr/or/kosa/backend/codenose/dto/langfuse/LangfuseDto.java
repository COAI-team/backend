package kr.or.kosa.backend.codenose.dto.langfuse;

import java.util.List;
import java.util.Map;

public class LangfuseDto {
    public record BatchRequest(List<Event> batch) {
    }

    public record Event(String id, String type, String timestamp, Object body) {
    }

    public record TraceBody(
            String id,
            String name,
            String userId,
            Map<String, Object> metadata,
            String release,
            String version) {
    }

    public record SpanBody(
            String id,
            String traceId,
            String parentObservationId,
            String name,
            String startTime,
            String endTime,
            Map<String, Object> input,
            Map<String, Object> output,
            Map<String, Object> metadata,
            String level,
            String statusMessage) {
    }

    public record GenerationBody(
            String id,
            String traceId,
            String parentObservationId,
            String name,
            String startTime,
            String endTime,
            String model,
            Map<String, Object> modelParameters,
            Object input,
            Object output,
            Map<String, Object> metadata,
            String level,
            String statusMessage,
            Usage usage) {
    }

    public record Usage(
            int promptTokens,
            int completionTokens,
            int totalTokens,
            String unit // "TOKENS"
    ) {
    }
}
