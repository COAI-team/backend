package kr.or.kosa.backend.battle.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.Length;

@Getter
@Setter
public class BattleRoomUpdateRequest {

    @Length(max = 100)
    private String title;

    private Long algoProblemId;

    private Long languageId;

    private String levelMode;

    @PositiveOrZero
    private Integer maxDurationMinutes;

    @DecimalMin(value = "0", inclusive = true, message = "베팅 금액은 0 이상이어야 합니다.")
    private java.math.BigDecimal betAmount;

    private Boolean isPrivate;

    @Length(max = 4)
    @Pattern(regexp = "^\\d{4}$", message = "비밀번호는 숫자 4자리여야 합니다.")
    private String newPassword;
}
