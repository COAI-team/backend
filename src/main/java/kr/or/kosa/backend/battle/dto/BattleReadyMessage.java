package kr.or.kosa.backend.battle.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BattleReadyMessage {
    @NotBlank
    private String roomId;
    private boolean ready = true;
}
