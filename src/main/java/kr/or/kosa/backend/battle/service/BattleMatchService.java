package kr.or.kosa.backend.battle.service;

import kr.or.kosa.backend.algorithm.mapper.DailyMissionMapper;
import kr.or.kosa.backend.algorithm.dto.UserAlgoLevelDto;
import kr.or.kosa.backend.battle.domain.BattleMatch;
import kr.or.kosa.backend.battle.domain.BattleRoomState;
import kr.or.kosa.backend.battle.domain.BattleSettlementStatus;
import kr.or.kosa.backend.battle.domain.BattleStatus;
import kr.or.kosa.backend.battle.mapper.BattleMatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BattleMatchService {

    private final BattleMatchMapper battleMatchMapper;
    private final DailyMissionMapper dailyMissionMapper;

    @Transactional
    public void createMatch(BattleRoomState state) {
        String levelMode = normalizeLevelMode(state.getLevelMode());
        String hostLevelSnapshot = fetchLevelSnapshot(state.getHostUserId());
        String guestLevelSnapshot = fetchLevelSnapshot(state.getGuestUserId());
        BattleMatch match = BattleMatch.builder()
                .matchId(state.getMatchId())
                .status(state.getStatus())
                .hostUserId(state.getHostUserId())
                .guestUserId(state.getGuestUserId())
                .algoProblemId(state.getAlgoProblemId())
                .languageId(state.getLanguageId())
                .levelMode(levelMode)
                .hostLevelSnapshot(hostLevelSnapshot)
                .guestLevelSnapshot(guestLevelSnapshot)
                .roomTitle(state.getTitle())
                .betAmount(state.getBetAmount())
                .maxDurationMinutes(state.getMaxDurationMinutes())
                .settlementStatus(BattleSettlementStatus.NONE)
                .build();

        battleMatchMapper.insert(match);
    }

    private String normalizeLevelMode(String levelMode) {
        if (levelMode == null || levelMode.isBlank()) return "ANY";
        String upper = levelMode.trim().toUpperCase();
        if (upper.contains("SAME")) return "SAME_LINE_ONLY";
        if (upper.contains("ANY") || upper.contains("NONE") || upper.contains("UNLIMITED")) return "ANY";
        return upper.length() > 10 ? "ANY" : upper;
    }

    @Transactional
    public void updateParticipants(String matchId, Long hostUserId, Long guestUserId) {
        String hostLevelSnapshot = fetchLevelSnapshot(hostUserId);
        String guestLevelSnapshot = fetchLevelSnapshot(guestUserId);
        battleMatchMapper.updateParticipants(matchId, hostUserId, guestUserId, hostLevelSnapshot, guestLevelSnapshot);
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
        String normalized = normalizeWinReasonForDb(winReason);
        battleMatchMapper.updateStatus(matchId, BattleStatus.FINISHED.name(), winnerUserId, normalized);
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

    @Transactional
    public void updateProblem(String matchId, Long algoProblemId) {
        if (matchId == null || algoProblemId == null) return;
        battleMatchMapper.updateProblem(matchId, algoProblemId);
    }

    @Transactional
    public void recordAccepted(String matchId, Long userId, Long hostUserId, Long guestUserId, LocalDateTime acceptedAt) {
        if (matchId == null || userId == null || acceptedAt == null) return;
        if (hostUserId != null && userId.equals(hostUserId)) {
            battleMatchMapper.updateHostAcAt(matchId, acceptedAt);
            return;
        }
        if (guestUserId != null && userId.equals(guestUserId)) {
            battleMatchMapper.updateGuestAcAt(matchId, acceptedAt);
        }
    }

    @Transactional
    public void updateWinnerElapsedMs(String matchId, Integer winnerElapsedMs) {
        if (matchId == null || winnerElapsedMs == null) return;
        battleMatchMapper.updateWinnerElapsedMs(matchId, winnerElapsedMs);
    }

    @Transactional(readOnly = true)
    public Optional<BattleMatch> findById(String matchId) {
        return battleMatchMapper.findById(matchId);
    }

    private String fetchLevelSnapshot(Long userId) {
        if (userId == null) return null;
        try {
            UserAlgoLevelDto level = dailyMissionMapper.findUserLevel(userId);
            if (level == null || level.getAlgoLevel() == null) return null;
            return level.getAlgoLevel().name();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeWinReasonForDb(String winReason) {
        if (winReason == null) return null;
        String upper = winReason.trim().toUpperCase();
        if ("SCORE".equals(upper) || "ACCEPTED".equals(upper)) return "FIRST_AC";
        if ("DRAW".equals(upper)) return "CANCELED";
        return upper;
    }
}
