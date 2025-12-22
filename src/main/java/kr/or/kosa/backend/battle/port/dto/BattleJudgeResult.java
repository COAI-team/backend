package kr.or.kosa.backend.battle.port.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BattleJudgeResult {
    private final boolean accepted;
    private final String message;

    public static BattleJudgeResult accepted() {
        return BattleJudgeResult.builder()
                .accepted(true)
                .message("채점 완료")
                .build();
    }

    public static BattleJudgeResult rejected(String message) {
        return BattleJudgeResult.builder()
                .accepted(false)
                .message(message)
                .build();
    }
}
