package kr.or.kosa.backend.battle.util;

public final class BattleRedisKeyUtil {

    private BattleRedisKeyUtil() {
    }

    public static String roomKey(String roomId) {
        return "battle:room:" + roomId;
    }

    public static String lobbyKey() {
        return "battle:lobby:rooms";
    }

    public static String lockKey(String roomId) {
        return "battle:lock:" + roomId;
    }

    public static String userLockKey(Long userId) {
        return "battle:lock:user:" + userId;
    }

    public static String activeRoomKey(Long userId) {
        return "battle:user:" + userId + ":activeRoom";
    }

    public static String membersKey(String roomId) {
        return "battle:room:" + roomId + ":members";
    }

    public static String passwordAttemptKey(String roomId, Long userId) {
        return "battle:pw:attempt:" + roomId + ":" + userId;
    }

    public static String passwordLockKey(String roomId, Long userId) {
        return "battle:pw:lock:" + roomId + ":" + userId;
    }

    public static String kickedKey(String roomId) {
        return "battle:room:" + roomId + ":kicked";
    }
}
