package kr.or.kosa.backend.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 * Piston API WebClient 설정
 * 로컬 개발 환경에서 Judge0 대신 사용
 */
@Configuration
@Slf4j
public class PistonConfig {

    @Value("${piston.api.base-url:https://emkc.org/api/v2/piston}")
    private String baseUrl;

    @Value("${piston.api.timeout:30000}")
    private Integer timeout;

    @Bean("pistonWebClient")
    public WebClient pistonWebClient() {
        log.info("Piston WebClient 설정 시작 - BaseURL: {}", baseUrl);

        // Connection Provider 설정
        ConnectionProvider connectionProvider = ConnectionProvider.builder("piston-pool")
                .maxConnections(20)
                .build();

        // HTTP Client 설정
        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .responseTimeout(Duration.ofMillis(timeout))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(timeout, TimeUnit.MILLISECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS)))
                .compress(true);

        // Exchange Strategies 설정
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> {
                    configurer.defaultCodecs().maxInMemorySize(1024 * 1024);
                })
                .build();

        // WebClient 생성
        WebClient webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .filter((request, next) -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Piston API 요청: {} {}", request.method(), request.url());
                    }

                    long startTime = System.currentTimeMillis();
                    return next.exchange(request)
                            .doOnNext(response -> {
                                long elapsed = System.currentTimeMillis() - startTime;
                                log.debug("Piston API 응답: {} - {}ms", response.statusCode(), elapsed);
                            })
                            .doOnError(error -> {
                                long elapsed = System.currentTimeMillis() - startTime;
                                log.error("Piston API 오류: {} - {}ms", error.getMessage(), elapsed);
                            });
                })
                .build();

        log.info("Piston WebClient 설정 완료");
        return webClient;
    }
}
