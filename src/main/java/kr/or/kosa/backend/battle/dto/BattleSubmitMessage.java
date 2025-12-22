package kr.or.kosa.backend.battle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BattleSubmitMessage {

    @NotBlank
    private String roomId;

    @NotNull
    private Long languageId;

    @NotNull
    private Long problemId;

    @NotBlank
    private String source;
}
