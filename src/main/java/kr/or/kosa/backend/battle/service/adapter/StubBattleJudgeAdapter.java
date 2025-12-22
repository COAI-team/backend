package kr.or.kosa.backend.battle.service.adapter;

import kr.or.kosa.backend.battle.port.BattleJudgePort;
import kr.or.kosa.backend.battle.port.dto.BattleJudgeCommand;
import kr.or.kosa.backend.battle.port.dto.BattleJudgeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StubBattleJudgeAdapter implements BattleJudgePort {

    @Override
    public BattleJudgeResult judge(BattleJudgeCommand command) {
        log.warn("[battle] judge stub invoked for matchId={} userId={}", command.getMatchId(), command.getUserId());
        return BattleJudgeResult.rejected("채점 서비스가 준비되지 않았습니다.");
    }
}
