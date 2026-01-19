package kr.or.kosa.backend.codenose.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Langfuse Tracing/Span Observability Annotation
 * Appling this annotation to a method will automatically create a trace/spam in
 * Langfuse.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LangfuseObserve {
    String name() default ""; // Name of trace/span (default: method name)

    boolean captureInput() default true; // Whether to capture arguments

    boolean captureOutput() default true; // Whether to capture return value
}
