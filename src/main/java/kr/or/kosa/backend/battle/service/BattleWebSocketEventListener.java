package kr.or.kosa.backend.battle.service;

import java.security.Principal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class BattleWebSocketEventListener {

    private final BattleRoomService battleRoomService;

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        Long userId = resolveUserId(event.getUser());
        if (userId == null) return;
        battleRoomService.handleDisconnect(userId);
        log.info("[battle-ws] userId={} action=disconnect", userId);
    }

    private Long resolveUserId(Principal user) {
        if (user == null) return null;
        try {
            return Long.valueOf(user.getName());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
