package kr.or.kosa.backend.battle.mapper;

import java.util.List;
import java.util.Optional;

import kr.or.kosa.backend.battle.domain.BattleMatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BattleMatchMapper {
    int insert(BattleMatch battleMatch);

    int updateParticipants(@Param("matchId") String matchId,
                           @Param("hostUserId") Long hostUserId,
                           @Param("guestUserId") Long guestUserId,
                           @Param("hostLevelSnapshot") String hostLevelSnapshot,
                           @Param("guestLevelSnapshot") String guestLevelSnapshot);

    int updateStatus(@Param("matchId") String matchId,
                     @Param("status") String status,
                     @Param("winnerUserId") Long winnerUserId,
                     @Param("winReason") String winReason);

    int updateCountdownStartedAt(@Param("matchId") String matchId,
                                 @Param("countdownStartedAt") java.time.LocalDateTime countdownStartedAt);

    int updateStartedAt(@Param("matchId") String matchId,
                        @Param("startedAt") java.time.LocalDateTime startedAt);

    int updateSettlementStatus(@Param("matchId") String matchId,
                               @Param("settlementStatus") String settlementStatus);

    int updateMaxDuration(@Param("matchId") String matchId,
                          @Param("maxDurationMinutes") Integer maxDurationMinutes);

    int updateProblem(@Param("matchId") String matchId,
                      @Param("algoProblemId") Long algoProblemId);

    int updateHostAcAt(@Param("matchId") String matchId,
                       @Param("acAt") java.time.LocalDateTime acAt);

    int updateGuestAcAt(@Param("matchId") String matchId,
                        @Param("acAt") java.time.LocalDateTime acAt);

    int updateWinnerElapsedMs(@Param("matchId") String matchId,
                              @Param("winnerElapsedMs") Integer winnerElapsedMs);

    Optional<BattleMatch> findById(@Param("matchId") String matchId);

    List<BattleMatch> findActiveMatches();
}
