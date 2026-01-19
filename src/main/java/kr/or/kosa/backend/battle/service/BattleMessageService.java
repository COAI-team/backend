package kr.or.kosa.backend.battle.service;

import java.util.List;

import kr.or.kosa.backend.battle.domain.BattleEventType;
import kr.or.kosa.backend.battle.domain.BattleRoomState;
import kr.or.kosa.backend.battle.dto.BattleErrorMessage;
import kr.or.kosa.backend.battle.dto.BattleFinishResponse;
import kr.or.kosa.backend.battle.dto.BattleRoomListResponse;
import kr.or.kosa.backend.battle.dto.BattleRoomResponse;
import kr.or.kosa.backend.battle.dto.BattleSubmitResultResponse;
import kr.or.kosa.backend.battle.dto.BattleWsMessage;
import kr.or.kosa.backend.battle.exception.BattleErrorCode;
import kr.or.kosa.backend.commons.exception.custom.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BattleMessageService {

    private final SimpMessagingTemplate messagingTemplate;
    private final BattleMatchService battleMatchService;

    public void publishRoomList(List<BattleRoomResponse> rooms) {
        BattleRoomListResponse response = BattleRoomListResponse.builder()
                .rooms(rooms)
                .build();
        BattleWsMessage<BattleRoomListResponse> message = BattleWsMessage.<BattleRoomListResponse>builder()
                .type(BattleEventType.ROOM_LIST)
                .payload(response)
                .build();
        messagingTemplate.convertAndSend("/topic/battle/rooms", message);
    }

    public void publishRoomState(BattleRoomState state) {
        BattleWsMessage<BattleRoomResponse> message = BattleWsMessage.<BattleRoomResponse>builder()
                .type(BattleEventType.ROOM_STATE)
                .roomId(state.getRoomId())
                .matchId(state.getMatchId())
                .payload(BattleRoomResponse.from(state))
                .build();
        messagingTemplate.convertAndSend("/topic/battle/room/" + state.getRoomId(), message);
    }

    public void publishCountdown(BattleRoomState state, int secondsLeft) {
        BattleWsMessage<Integer> message = BattleWsMessage.<Integer>builder()
                .type(BattleEventType.COUNTDOWN)
                .roomId(state.getRoomId())
                .matchId(state.getMatchId())
                .payload(secondsLeft)
                .build();
        messagingTemplate.convertAndSend("/topic/battle/room/" + state.getRoomId(), message);
    }

    public void publishStart(BattleRoomState state) {
        BattleWsMessage<BattleRoomResponse> message = BattleWsMessage.<BattleRoomResponse>builder()
                .type(BattleEventType.START)
                .roomId(state.getRoomId())
                .matchId(state.getMatchId())
                .payload(BattleRoomResponse.from(state))
                .build();
        messagingTemplate.convertAndSend("/topic/battle/room/" + state.getRoomId(), message);
    }

    public void publishSubmitResult(BattleSubmitResultResponse response, String roomId, String matchId) {
        BattleWsMessage<BattleSubmitResultResponse> message = BattleWsMessage.<BattleSubmitResultResponse>builder()
                .type(BattleEventType.SUBMIT_RESULT)
                .roomId(roomId)
                .matchId(matchId)
                .payload(response)
                .build();
        messagingTemplate.convertAndSend("/topic/battle/room/" + roomId, message);
    }

    public void publishFinish(BattleRoomState state) {
        BattleFinishResponse payload = BattleFinishResponse.from(
                state,
                battleMatchService.findById(state.getMatchId()).orElse(null)
        );
        if (payload != null) {
            log.info("[battle] matchId={} roomId={} action=broadcast-finish winner={} reason={} hostScore={} guestScore={}",
                    state.getMatchId(), state.getRoomId(), payload.getWinnerUserId(), payload.getWinReason(),
                    payload.getHost() != null ? payload.getHost().getFinalScore() : null,
                    payload.getGuest() != null ? payload.getGuest().getFinalScore() : null);
        }
        BattleWsMessage<BattleFinishResponse> message = BattleWsMessage.<BattleFinishResponse>builder()
                .type(BattleEventType.FINISH)
                .roomId(state.getRoomId())
                .matchId(state.getMatchId())
                .payload(payload)
                .build();
        messagingTemplate.convertAndSend("/topic/battle/room/" + state.getRoomId(), message);
    }

    public void sendErrorToUser(Long userId, ErrorCode errorCode) {
        sendErrorToUser(userId, errorCode, null);
    }

    public void sendErrorToUser(Long userId, ErrorCode errorCode, String customMessage) {
        BattleErrorMessage payload = BattleErrorMessage.builder()
                .code(errorCode.getCode())
                .message(customMessage != null ? customMessage : errorCode.getMessage())
                .build();
        BattleWsMessage<BattleErrorMessage> message = BattleWsMessage.<BattleErrorMessage>builder()
                .type(BattleEventType.ERROR)
                .payload(payload)
                .build();
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/battle", message);
    }
}
