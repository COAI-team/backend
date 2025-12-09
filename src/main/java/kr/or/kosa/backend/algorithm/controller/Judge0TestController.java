package kr.or.kosa.backend.algorithm.controller;

import jakarta.annotation.PostConstruct;
import kr.or.kosa.backend.config.Judge0Config.Judge0Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Judge0 설정 확인용 테스트 컨트롤러
 */
@RestController
@Component
@RequiredArgsConstructor
@Slf4j
public class Judge0TestController {

    private final WebClient judge0WebClient;
    private final Judge0Properties judge0Properties;

    // 생성자 추가 (Bean 등록 확인용)
    @PostConstruct
    public void init() {
        log.info("=== Judge0TestController Bean 등록 성공! ===");
        log.info("BaseURL: {}", judge0Properties.getBaseUrl());
    }
    /**
     * Judge0 설정 확인
     */
    @GetMapping("/api/test/judge0-config")
    public Map<String, Object> testJudge0Config() {
        boolean apiKeySet = judge0Properties.getRapidapiKey() != null &&
                !judge0Properties.getRapidapiKey().isEmpty() &&
                !judge0Properties.getRapidapiKey().equals("${JUDGE0_API_KEY}");

        return Map.of(
                "baseUrl", judge0Properties.getBaseUrl(),
                "apiKeySet", apiKeySet,
                "timeoutResponse", judge0Properties.getTimeout().getResponse(),
                "maxRetryAttempts", judge0Properties.getRetry().getMaxAttempts(),
                "supportedLanguages", judge0Properties.getLanguages().keySet(),
                "status", apiKeySet ? "Judge0 설정이 정상입니다" : "API 키를 확인해주세요"
        );
    }

    /**
     * Judge0 API 실제 연결 테스트 (개선된 버전)
     */
    @GetMapping("/api/test/judge0-connection")
    public Mono<Map<String, Object>> testJudge0Connection() {
        log.info("Judge0 API 연결 테스트 시작...");

        return judge0WebClient
                .get()
                .uri("/config_info")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .map(response -> {
                    log.info("Judge0 API 연결 성공");
                    Map<String, Object> successResult = new java.util.HashMap<>();
                    successResult.put("connectionStatus", "SUCCESS");
                    successResult.put("message", "Judge0 API 연결 성공");
                    successResult.put("hasResponse", response != null && !response.isEmpty());
                    return successResult;
                })
                .onErrorReturn(createErrorResponse());
    }

    /**
     * 에러 응답 생성 헬퍼 메서드
     */
    private Map<String, Object> createErrorResponse() {
        Map<String, Object> errorResult = new java.util.HashMap<>();
        errorResult.put("connectionStatus", "FAILED");
        errorResult.put("message", "Judge0 API 연결 실패 - API 키나 네트워크를 확인해주세요");
        return errorResult;
    }
}
