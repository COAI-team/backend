package kr.or.kosa.backend.battle.service;

import kr.or.kosa.backend.battle.dto.BattleSubmitMessage;
import kr.or.kosa.backend.battle.port.BattleJudgePort;
import kr.or.kosa.backend.battle.port.dto.BattleJudgeCommand;
import kr.or.kosa.backend.battle.port.dto.BattleJudgeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BattleJudgeService {

    private final BattleJudgePort battleJudgePort;

    public BattleJudgeResult judge(String matchId, Long userId, BattleSubmitMessage submitMessage) {
        BattleJudgeCommand command = BattleJudgeCommand.builder()
                .matchId(matchId)
                .userId(userId)
                .problemId(submitMessage.getProblemId())
                .languageId(submitMessage.getLanguageId())
                .source(submitMessage.getSource())
                .build();
        BattleJudgeResult result = battleJudgePort.judge(command);
        log.info("[battle] matchId={} userId={} action=judge accepted={}", matchId, userId, result.isAccepted());
        return result;
    }
}
