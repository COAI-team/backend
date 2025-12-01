package kr.or.kosa.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 처리 설정
 * - @Async 어노테이션 활성화
 * - 커스텀 스레드 풀 설정
 * - 예외 처리 설정
 */
@Slf4j
@Configuration
@EnableAsync(proxyTargetClass = true) // ✅ CGLIB 프록시 사용
public class AsyncConfiguration implements AsyncConfigurer {

    /**
     * 기본 비동기 실행자 설정
     */
    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        log.info("🚀 AsyncConfiguration: 기본 TaskExecutor 설정 중...");

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);        // 코어 스레드 수
        executor.setMaxPoolSize(16);        // 최대 스레드 수
        executor.setQueueCapacity(100);     // 큐 용량
        executor.setThreadNamePrefix("Async-Default-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        log.info("✅ AsyncConfiguration: 기본 TaskExecutor 설정 완료 (코어: 8, 최대: 16)");
        return executor;
    }

    /**
     * AI 평가 전용 스레드 풀
     */
    @Bean(name = "aiEvaluationExecutor")
    public Executor aiEvaluationExecutor() {
        log.info("🤖 AsyncConfiguration: AI 평가 전용 TaskExecutor 설정 중...");

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);        // AI 평가는 CPU 집약적이므로 적게
        executor.setMaxPoolSize(8);         // 최대 8개까지
        executor.setQueueCapacity(50);      // 큐 용량
        executor.setThreadNamePrefix("AI-Evaluation-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120); // AI 처리 시간 고려해서 더 길게
        executor.initialize();

        log.info("✅ AsyncConfiguration: AI 평가 TaskExecutor 설정 완료 (코어: 4, 최대: 8)");
        return executor;
    }

    /**
     * Judge0 채점 전용 스레드 풀
     */
    @Bean(name = "judgeExecutor")
    public Executor judgeExecutor() {
        log.info("⚖️ AsyncConfiguration: Judge0 전용 TaskExecutor 설정 중...");

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(6);        // Judge0 API 호출용
        executor.setMaxPoolSize(12);        // 네트워크 I/O가 주요하므로 많이
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("Judge0-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(90);
        executor.initialize();

        log.info("✅ AsyncConfiguration: Judge0 TaskExecutor 설정 완료 (코어: 6, 최대: 12)");
        return executor;
    }

    /**
     * 비동기 메서드에서 예외 발생 시 처리
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, objects) -> {
            log.error("🚨 비동기 메서드에서 예외 발생!");
            log.error("메서드: {}", method.getName());
            log.error("파라미터: {}", java.util.Arrays.toString(objects));
            log.error("예외 내용:", throwable);
        };
    }
}