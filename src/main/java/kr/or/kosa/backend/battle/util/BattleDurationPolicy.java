package kr.or.kosa.backend.battle.util;

import org.springframework.stereotype.Component;

/**
 * 난이도 기반 기본 최대 진행시간(분) 계산용 유틸.
 * 문제 난이도를 알 수 없을 때는 20분을 기본값으로 사용한다.
 */
@Component
public class BattleDurationPolicy {

    public int defaultMinutes(String difficulty) {
        if (difficulty == null) {
            return 20;
        }
        String upper = difficulty.toUpperCase();
        return switch (upper) {
            case "BRONZE" -> 10;
            case "SILVER" -> 20;
            case "GOLD" -> 30;
            case "PLATINUM" -> 40;
            default -> 20;
        };
    }

    public int effectiveMinutes(Integer requested, String difficulty) {
        if (requested != null && requested > 0) {
            return Math.min(120, Math.max(1, requested));
        }
        return defaultMinutes(difficulty);
    }
}
