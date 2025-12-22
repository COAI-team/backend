package kr.or.kosa.backend.battle.controller;

import java.security.Principal;

import jakarta.validation.Valid;
import kr.or.kosa.backend.battle.dto.BattleReadyMessage;
import kr.or.kosa.backend.battle.dto.BattleSubmitMessage;
import kr.or.kosa.backend.battle.exception.BattleException;
import kr.or.kosa.backend.battle.service.BattleMessageService;
import kr.or.kosa.backend.battle.service.BattleRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class BattleWebSocketController {

    private final BattleRoomService battleRoomService;
    private final BattleMessageService battleMessageService;

    @MessageMapping("/battle/ready")
    public void ready(@Payload @Valid BattleReadyMessage message, Principal principal) {
        Long userId = Long.valueOf(principal.getName());
        try {
            battleRoomService.ready(message.getRoomId(), userId, message.isReady());
        } catch (BattleException ex) {
            handleBattleException(ex, principal);
        } catch (Exception ex) {
            log.error("[battle] userId={} action=ready error={}", userId, ex.getMessage(), ex);
            battleMessageService.sendErrorToUser(userId, kr.or.kosa.backend.battle.exception.BattleErrorCode.INVALID_STATUS);
        }
    }

    @MessageMapping("/battle/submit")
    public void submit(@Payload @Valid BattleSubmitMessage message, Principal principal) {
        Long userId = Long.valueOf(principal.getName());
        try {
            battleRoomService.submit(userId, message);
        } catch (BattleException ex) {
            handleBattleException(ex, principal);
        } catch (Exception ex) {
            log.error("[battle] userId={} action=submit error={}", userId, ex.getMessage(), ex);
            battleMessageService.sendErrorToUser(userId, kr.or.kosa.backend.battle.exception.BattleErrorCode.INVALID_STATUS);
        }
    }

    @MessageExceptionHandler(BattleException.class)
    public void handleBattleException(BattleException ex, Principal principal) {
        Long userId = principal != null ? Long.valueOf(principal.getName()) : null;
        log.warn("[battle] userId={} errorCode={} message={}", userId, ex.getErrorCode().getCode(), ex.getMessage());
        if (userId != null) {
            battleMessageService.sendErrorToUser(userId, ex.getErrorCode());
        }
    }
}
