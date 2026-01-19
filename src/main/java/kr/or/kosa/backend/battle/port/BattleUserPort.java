package kr.or.kosa.backend.battle.port;

import java.util.Optional;

import kr.or.kosa.backend.battle.port.dto.BattleUserProfile;

public interface BattleUserPort {
    Optional<BattleUserProfile> findProfile(Long userId);
}
