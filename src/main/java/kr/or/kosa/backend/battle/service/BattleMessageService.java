package kr.or.kosa.backend.battle.service;

import java.util.List;

import kr.or.kosa.backend.battle.domain.BattleEventType;
import kr.or.kosa.backend.battle.domain.BattleRoomState;
import kr.or.kosa.backend.battle.dto.BattleErrorMessage;
import kr.or.kosa.backend.battle.dto.BattleRoomListResponse;
import kr.or.kosa.backend.battle.dto.BattleRoomResponse;
import kr.or.kosa.backend.battle.dto.BattleSubmitResultResponse;
import kr.or.kosa.backend.battle.dto.BattleWsMessage;
import kr.or.kosa.backend.battle.exception.BattleErrorCode;
import kr.or.kosa.backend.commons.exception.custom.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BattleMessageService {

    private final SimpMessagingTemplate messagingTemplate;

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
        BattleWsMessage<BattleRoomResponse> message = BattleWsMessage.<BattleRoomResponse>builder()
                .type(BattleEventType.FINISH)
                .roomId(state.getRoomId())
                .matchId(state.getMatchId())
                .payload(BattleRoomResponse.from(state))
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
