package kr.or.kosa.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Judge0 API 설정 프로퍼티
 */
@Data
@Component
@ConfigurationProperties(prefix = "judge0.api")
public class Judge0Properties {

    private String baseUrl;
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
