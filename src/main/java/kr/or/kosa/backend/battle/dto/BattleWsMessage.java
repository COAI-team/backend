package kr.or.kosa.backend.battle.dto;

import kr.or.kosa.backend.battle.domain.BattleEventType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BattleWsMessage<T> {
    private final BattleEventType type;
    private final String roomId;
    private final String matchId;
    private final T payload;
}
