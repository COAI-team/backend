package kr.or.kosa.backend.battle.config;

import kr.or.kosa.backend.security.jwt.JwtAuthentication;
import kr.or.kosa.backend.security.jwt.JwtProvider;
import kr.or.kosa.backend.security.jwt.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class BattleStompAuthInterceptor implements ChannelInterceptor {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = resolveToken(accessor.getFirstNativeHeader(AUTH_HEADER));
            if (token != null && jwtProvider.validateToken(token)) {
                Long userId = jwtProvider.getUserId(token);
                String email = jwtProvider.getEmail(token);
                JwtUserDetails userDetails = new JwtUserDetails(userId, email);
                JwtAuthentication authentication = new JwtAuthentication(userDetails);
                accessor.setUser(authentication);
                log.info("[battle-ws] authenticated userId={}", userId);
            } else {
                log.warn("[battle-ws] CONNECT without valid token");
            }
        }

        if (accessor.getUser() instanceof JwtAuthentication auth) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
        }
        return message;
    }

    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
        SecurityContextHolder.clearContext();
    }

    private String resolveToken(String authHeader) {
        if (!StringUtils.hasText(authHeader)) {
            return null;
        }
        if (authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }
        return authHeader;
    }
}
