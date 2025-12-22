package kr.or.kosa.backend.battle.port;

import kr.or.kosa.backend.battle.port.dto.BattleJudgeCommand;
import kr.or.kosa.backend.battle.port.dto.BattleJudgeResult;

public interface BattleJudgePort {
    BattleJudgeResult judge(BattleJudgeCommand command);
}
