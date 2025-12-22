package kr.or.kosa.backend.battle.service.adapter;

import java.util.Optional;

import kr.or.kosa.backend.battle.port.BattleUserPort;
import kr.or.kosa.backend.battle.port.dto.BattleUserProfile;
import kr.or.kosa.backend.users.domain.Users;
import kr.or.kosa.backend.users.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BattleUserAdapter implements BattleUserPort {

    private final UserMapper userMapper;

    @Override
    public Optional<BattleUserProfile> findProfile(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        Users user = userMapper.findById(userId);
        if (user == null) {
            return Optional.empty();
        }
        return Optional.of(
                BattleUserProfile.builder()
                        .userId(userId)
                        .nickname(user.getUserNickname())
                        .build()
        );
    }
}
