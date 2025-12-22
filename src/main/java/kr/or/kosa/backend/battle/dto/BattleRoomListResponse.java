package kr.or.kosa.backend.battle.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BattleRoomListResponse {
    private final List<BattleRoomResponse> rooms;
}
