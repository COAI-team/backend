package kr.or.kosa.backend.codenose.service.trace;

import java.util.Stack;

/**
 * Langfuse Trace Context (ThreadLocal)
 * 
 * Stores the current Trace ID and a stack of Parent Observation IDs
 * to implicitly link spans and generations across the call stack.
 */
public class LangfuseContext {
    private static final ThreadLocal<String> currentTraceId = new ThreadLocal<>();
    private static final ThreadLocal<Stack<SpanInfo>> parentStack = ThreadLocal.withInitial(Stack::new);

    public record SpanInfo(String id, String name, String startTime, Object input) {
    }

    public static void setTraceId(String traceId) {
        currentTraceId.set(traceId);
        parentStack.get().clear(); // New trace, reset stack
    }

    public static String getTraceId() {
        return currentTraceId.get();
    }

    public static void clean() {
        currentTraceId.remove();
        parentStack.remove();
    }

    /**
     * Pushes a new parent observation.
     */
    public static void pushParentSpan(SpanInfo spanInfo) {
        parentStack.get().push(spanInfo);
    }

    /**
     * Pops the last parent observation info.
     */
    public static SpanInfo popParentSpan() {
        if (!parentStack.get().isEmpty()) {
            return parentStack.get().pop();
        }
        return null;
    }

    /**
     * Peeks at the current parent SpanInfo.
     */
    public static SpanInfo getCurrentSpanInfo() {
        if (!parentStack.get().isEmpty()) {
            return parentStack.get().peek();
        }
        return null;
    }

    /**
     * Helper to get just the ID for compatibility.
     */
    public static String getCurrentParentId() {
        if (!parentStack.get().isEmpty()) {
            return parentStack.get().peek().id();
        }
        return null;
    }

    public static boolean hasParent() {
        return !parentStack.get().isEmpty();
    }
}
