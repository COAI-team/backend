package kr.or.kosa.backend.config;

import kr.or.kosa.backend.algorithm.controller.interceptor.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Rate Limiting 인터셉터 설정
 */
@Configuration
@RequiredArgsConstructor
public class RateLimitConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/algo/problems/generate/**")  // 문제 생성 (POST + SSE GET)
                .addPathPatterns("/algo/submissions/**")        // 코드 제출
                .addPathPatterns("/algo/pool/draw")             // 풀에서 문제 뽑기 (POST 동기 방식)
                .addPathPatterns("/analysis/analyze-stored")    // 코드 분석
                .excludePathPatterns("/algo/pool/draw/stream"); // SSE 스트리밍은 컨트롤러에서 직접 처리 (재연결 중복 방지)
    }
}
