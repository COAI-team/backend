package kr.or.kosa.backend.battle.service;

import java.time.LocalDateTime;

import kr.or.kosa.backend.battle.domain.BattleMatch;
import kr.or.kosa.backend.battle.domain.BattleRoomState;
import kr.or.kosa.backend.battle.domain.BattleSettlementStatus;
import kr.or.kosa.backend.battle.domain.BattleStatus;
import kr.or.kosa.backend.battle.mapper.BattleMatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BattleMatchService {

    private final BattleMatchMapper battleMatchMapper;

    @Transactional
    public void createMatch(BattleRoomState state) {
        BattleMatch match = BattleMatch.builder()
                .matchId(state.getMatchId())
                .status(state.getStatus())
                .hostUserId(state.getHostUserId())
                .guestUserId(state.getGuestUserId())
                .algoProblemId(state.getAlgoProblemId())
                .languageId(state.getLanguageId())
                .levelMode(state.getLevelMode())
                .roomTitle(state.getTitle())
                .betAmount(state.getBetAmount())
                .maxDurationMinutes(state.getMaxDurationMinutes())
                .settlementStatus(BattleSettlementStatus.NONE)
                .build();
        battleMatchMapper.insert(match);
    }

    @Transactional
    public void updateParticipants(String matchId, Long hostUserId, Long guestUserId) {
        battleMatchMapper.updateParticipants(matchId, hostUserId, guestUserId);
    }

    @Transactional
    public void markRunning(String matchId) {
        battleMatchMapper.updateStatus(matchId, BattleStatus.RUNNING.name(), null, null);
    }

    @Transactional
    public void markCountdown(String matchId) {
        battleMatchMapper.updateStatus(matchId, BattleStatus.COUNTDOWN.name(), null, null);
    }

    @Transactional
    public void markCanceled(String matchId) {
        battleMatchMapper.updateStatus(matchId, BattleStatus.CANCELED.name(), null, "CANCELED");
    }

    @Transactional
    public void finishMatch(String matchId, Long winnerUserId, String winReason) {
        battleMatchMapper.updateStatus(matchId, BattleStatus.FINISHED.name(), winnerUserId, winReason);
    }

    @Transactional
    public void updateSettlementStatus(String matchId, BattleSettlementStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("settlementStatus cannot be null");
        }
        String value = status.name();
        battleMatchMapper.updateSettlementStatus(matchId, value);
        org.slf4j.LoggerFactory.getLogger(BattleMatchService.class)
                .info("[battle] matchId={} action=updateSettlementStatus value={}", matchId, value);
    }

    @Transactional
    public void updateMaxDuration(String matchId, Integer maxDurationMinutes) {
        battleMatchMapper.updateMaxDuration(matchId, maxDurationMinutes);
    }
}
