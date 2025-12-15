package kr.or.kosa.backend.tutor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class TutorWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 운영 시에는 서비스 도메인 + localhost 정도만 Origin을 허용하고
        // '*' 와일드카드는 사용하지 않는 것이 권장됨. (실제 보안 설정은 Security 쪽에서 관리)
        registry.addEndpoint("/ws/tutor")
                .setAllowedOrigins(
                        "http://localhost:5173",
                        "http://localhost:3000"
                )
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic/tutor");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
