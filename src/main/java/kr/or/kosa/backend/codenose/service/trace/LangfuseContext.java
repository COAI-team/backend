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
    private static final ThreadLocal<Stack<String>> parentStack = ThreadLocal.withInitial(Stack::new);

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
     * Pushes a new parent observation ID (Span ID or Generation ID) onto the stack.
     * This usually happens when entering a span.
     */
    public static void pushParentId(String parentId) {
        parentStack.get().push(parentId);
    }

    /**
     * Pops the last parent observation ID.
     * This usually happens when exiting a span.
     */
    public static String popParentId() {
        if (!parentStack.get().isEmpty()) {
            return parentStack.get().pop();
        }
        return null; // Should not happen if balanced
    }

    /**
     * Peeks at the current parent ID (the most recent one).
     */
    public static String getCurrentParentId() {
        if (!parentStack.get().isEmpty()) {
            return parentStack.get().peek();
        }
        return null; // Top-level within trace
    }
}
