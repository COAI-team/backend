package kr.or.kosa.backend.battle.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.Instant;

public final class BattleTime {
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private BattleTime() {}

    public static LocalDateTime nowKst() {
        return LocalDateTime.now(KST);
    }

    public static Instant toInstant(LocalDateTime ldt) {
        return ldt == null ? null : ldt.atZone(KST).toInstant();
    }

    public static ZonedDateTime toZonedKst(LocalDateTime ldt) {
        return ldt == null ? null : ldt.atZone(KST);
    }

    public static String toIsoKst(LocalDateTime ldt) {
        return ldt == null ? null : ldt.atZone(KST).toOffsetDateTime().toString(); // 2025-12-23T17:20:35+09:00
    }

    public static String toIsoKst(Instant instant) {
        return instant == null ? null : instant.atZone(KST).toOffsetDateTime().toString();
    }

    public static LocalDateTime toLocalKst(Instant instant) {
        return instant == null ? null : instant.atZone(KST).toLocalDateTime();
    }
}
