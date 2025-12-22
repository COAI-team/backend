package kr.or.kosa.backend.battle.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import kr.or.kosa.backend.battle.jackson.InstantIsoDeserializer;
import kr.or.kosa.backend.battle.jackson.InstantIsoSerializer;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BattleParticipantState implements Serializable {
    private Long userId;
    private String nickname;
    private boolean ready;
    private boolean surrendered;
    private boolean finished;
    @JsonSerialize(using = InstantIsoSerializer.class)
    @JsonDeserialize(using = InstantIsoDeserializer.class)
    private Instant lastSubmittedAt;
    private Long elapsedSeconds;
    private BigDecimal baseScore;
    private BigDecimal timeBonus;
    private BigDecimal finalScore;
    private BigDecimal pointBalance;
}
