package kr.or.kosa.backend.battle.port.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BattleUserProfile {
    private final Long userId;
    private final String nickname;
    private final String level;
    private final Integer grade;
}
