package kr.or.kosa.backend.battle.mapper;

import kr.or.kosa.backend.battle.service.adapter.BattlePointHistoryRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BattlePointHistoryMapper {
    int insert(BattlePointHistoryRecord record);
}
