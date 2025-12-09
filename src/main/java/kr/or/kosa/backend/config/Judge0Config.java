package kr.or.kosa.backend.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Judge0 설정 클래스
 * - WebClient Bean 생성
 * - Judge0 API 프로퍼티 관리
 */
@Configuration
@EnableConfigurationProperties(Judge0Config.Judge0Properties.class)
@RequiredArgsConstructor
@Slf4j
public class Judge0Config {

    private final Judge0Properties judge0Properties;

    /**
     * Judge0 전용 WebClient Bean 생성
     */
    @Bean("judge0WebClient")
    public WebClient judge0WebClient() {

        log.info("Judge0 WebClient 설정 시작 - BaseURL: {}", judge0Properties.getBaseUrl());

        // 1. Connection Provider 설정
        ConnectionProvider connectionProvider = ConnectionProvider.builder("judge0-pool")
                .maxConnections(50)
                .build();

        // 2. HTTP Client 설정
        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .responseTimeout(Duration.ofSeconds(60))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(60, TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS)))
                .compress(true)
                .keepAlive(true);

        // 3. Exchange Strategies 설정
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> {
                    configurer.defaultCodecs().maxInMemorySize(1024 * 1024);
                })
                .build();

        // 4. WebClient 생성 (셀프호스팅/RapidAPI 모드 분기 처리)
        WebClient.Builder webClientBuilder = WebClient.builder()
                .baseUrl(judge0Properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json");

        // 셀프호스팅이 아닌 경우에만 RapidAPI 헤더 추가
        if (!judge0Properties.isSelfHosted()) {
            log.info("RapidAPI 모드로 Judge0 연결 - Host: {}", judge0Properties.getRapidapiHost());
            webClientBuilder
                    .defaultHeader("X-RapidAPI-Key", judge0Properties.getRapidapiKey())
                    .defaultHeader("X-RapidAPI-Host", judge0Properties.getRapidapiHost());
        } else {
            log.info("셀프호스팅 모드로 Judge0 연결 - URL: {}", judge0Properties.getBaseUrl());
        }

        WebClient webClient = webClientBuilder
                .filter((request, next) -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Judge0 API 요청: {} {}", request.method(), request.url());
                    }

                    long startTime = System.currentTimeMillis();
                    return next.exchange(request)
                            .doOnNext(response -> {
                                long elapsed = System.currentTimeMillis() - startTime;
                                log.debug("Judge0 API 응답: {} - {}ms",
                                        response.statusCode(), elapsed);
                            })
                            .doOnError(error -> {
                                long elapsed = System.currentTimeMillis() - startTime;
                                log.error("Judge0 API 오류: {} - {}ms",
                                        error.getMessage(), elapsed);
                            });
                })
                .build();

        log.info("Judge0 WebClient 설정 완료");
        return webClient;
    }

    /**
     * Judge0 API 설정 프로퍼티 (내부 클래스)
     * @EnableConfigurationProperties로 빈 등록되므로 @Component 불필요
     */
    @Data
    @ConfigurationProperties(prefix = "judge0.api")
    public static class Judge0Properties {

        private String baseUrl;
        private boolean selfHosted = true;  // 셀프호스팅 모드 (기본값: true)
        private String rapidapiKey;
        private String rapidapiHost;
        private Timeout timeout = new Timeout();
        private Retry retry = new Retry();
        private Limits limits = new Limits();
        private Map<String, Integer> languages = Map.of(
                "java", 62,
                "python", 71,
                "cpp", 54,
                "c", 50,
                "javascript", 63,
                "kotlin", 78,
                "go", 60,
                "rust", 73
        );

        @Data
        public static class Timeout {
            private String connection = "10s";
            private String response = "60s";
        }

        @Data
        public static class Retry {
            private int maxAttempts = 3;
            private String delay = "1s";
        }

        @Data
        public static class Limits {
            private Double cpuTime = 2.0;
            private Integer memory = 128000;
            private Double wallTime = 5.0;
        }
    }
}
