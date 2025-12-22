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
                           @Param("guestUserId") Long guestUserId);

    int updateStatus(@Param("matchId") String matchId,
                     @Param("status") String status,
                     @Param("winnerUserId") Long winnerUserId,
                     @Param("winReason") String winReason);

    int updateSettlementStatus(@Param("matchId") String matchId,
                               @Param("settlementStatus") String settlementStatus);

    int updateMaxDuration(@Param("matchId") String matchId,
                          @Param("maxDurationMinutes") Integer maxDurationMinutes);

    Optional<BattleMatch> findById(@Param("matchId") String matchId);

    List<BattleMatch> findActiveMatches();
}
