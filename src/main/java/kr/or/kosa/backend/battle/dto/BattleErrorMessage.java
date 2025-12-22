package kr.or.kosa.backend.battle.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BattleErrorMessage {
    private final String code;
    private final String message;
}
