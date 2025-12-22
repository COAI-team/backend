package kr.or.kosa.backend.battle.mapper;

import java.util.List;
import java.util.Optional;

import kr.or.kosa.backend.battle.domain.BattlePointHold;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BattlePointHoldMapper {
    int insert(BattlePointHold hold);

    int updateStatus(@Param("matchId") String matchId,
                     @Param("userId") Long userId,
                     @Param("status") String status,
                     @Param("pointHistoryId") Long pointHistoryId);

    Optional<BattlePointHold> findByMatchAndUser(@Param("matchId") String matchId,
                                                 @Param("userId") Long userId);

    List<BattlePointHold> findByMatchId(@Param("matchId") String matchId);
}
