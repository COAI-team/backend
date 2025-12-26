package kr.or.kosa.backend.battle.service;

import kr.or.kosa.backend.battle.dto.BattleSubmitMessage;
import kr.or.kosa.backend.battle.port.BattleJudgePort;
import kr.or.kosa.backend.battle.port.dto.BattleJudgeCommand;
import kr.or.kosa.backend.battle.port.dto.BattleJudgeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class BattleJudgeService {

    private static final long JUDGE_TIMEOUT_SECONDS = 25L;
    private static final ExecutorService JUDGE_EXECUTOR = Executors.newCachedThreadPool();

    private final BattleJudgePort battleJudgePort;

    public BattleJudgeResult judge(String matchId, Long userId, BattleSubmitMessage submitMessage) {
        BattleJudgeCommand command = BattleJudgeCommand.builder()
                .matchId(matchId)
                .userId(userId)
                .problemId(submitMessage.getProblemId())
                .languageId(submitMessage.getLanguageId())
                .source(submitMessage.getSource())
                .build();
        log.info("[battle] matchId={} userId={} action=judge-send problemId={} languageId={}",
                matchId, userId, submitMessage.getProblemId(), submitMessage.getLanguageId());
        BattleJudgeResult result = null;
        Future<BattleJudgeResult> future = JUDGE_EXECUTOR.submit(() -> battleJudgePort.judge(command));
        try {
            result = future.get(JUDGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("[battle] matchId={} userId={} action=judge-timeout timeoutSec={}",
                    matchId, userId, JUDGE_TIMEOUT_SECONDS);
            return null;
        } catch (Exception e) {
            log.warn("[battle] matchId={} userId={} action=judge-failed error={}",
                    matchId, userId, e.getMessage());
            return null;
        }
        if (result == null) {
            log.warn("[battle] matchId={} userId={} action=judge-null-result", matchId, userId);
            return null;
        }
        log.info("[battle] matchId={} userId={} action=judge accepted={} score={}",
                matchId, userId, result.isAccepted(), result.getScore());
        return result;
    }
}
