package kr.or.kosa.backend.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Judge0 WebClient 설정 클래스
 */
@Configuration
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

        // 4. WebClient 생성
        WebClient webClient = WebClient.builder()
                .baseUrl(judge0Properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .defaultHeader("X-RapidAPI-Key", judge0Properties.getRapidapiKey())
                .defaultHeader("X-RapidAPI-Host", judge0Properties.getRapidapiHost())
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
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
}
