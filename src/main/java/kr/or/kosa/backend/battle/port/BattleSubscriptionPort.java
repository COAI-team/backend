package kr.or.kosa.backend.battle.port;

public interface BattleSubscriptionPort {
    void notifyInvitation(Long targetUserId, String roomId);
}
