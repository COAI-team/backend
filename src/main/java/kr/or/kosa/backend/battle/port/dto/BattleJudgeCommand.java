package kr.or.kosa.backend.battle.port.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BattleJudgeCommand {
    private final String matchId;
    private final Long userId;
    private final Long problemId;
    private final Long languageId;
    private final String source;
}
