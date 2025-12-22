package kr.or.kosa.backend.battle.dto;

import java.util.Map;

import kr.or.kosa.backend.battle.util.BattleDurationPolicy;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DurationPolicyResponse {
    private final Map<String, Integer> difficultyDefaults;
    private final int minMinutes;
    private final int maxMinutes;
    private final String note;

    public static DurationPolicyResponse fromPolicy(BattleDurationPolicy policy) {
        return DurationPolicyResponse.builder()
                .difficultyDefaults(Map.of(
                        "BRONZE", policy.defaultMinutes("BRONZE"),
                        "SILVER", policy.defaultMinutes("SILVER"),
                        "GOLD", policy.defaultMinutes("GOLD"),
                        "PLATINUM", policy.defaultMinutes("PLATINUM"),
                        "DEFAULT", policy.defaultMinutes(null)
                ))
                .minMinutes(1)
                .maxMinutes(120)
                .note("난이도 기반 서버 기본시간입니다. 요청값이 없으면 기본값이 적용됩니다.")
                .build();
    }
}
