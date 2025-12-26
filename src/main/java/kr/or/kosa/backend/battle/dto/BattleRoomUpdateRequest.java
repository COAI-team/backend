package kr.or.kosa.backend.battle.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.Length;

@Getter
@Setter
public class BattleRoomUpdateRequest {

    @Length(max = 50)
    private String title;

    private Long algoProblemId;

    private Long languageId;

    private String levelMode;

    @PositiveOrZero
    @Min(1)
    @Max(120)
    private Integer maxDurationMinutes;

    @DecimalMin(value = "0", inclusive = true, message = "\uBCA0\uD305 \uAE08\uC561\uC740 0 \uC774\uC0C1\uC774\uC5B4\uC57C \uD569\uB2C8\uB2E4.")
    @jakarta.validation.constraints.DecimalMax(value = "99999", message = "\uBCA0\uD305 \uAE08\uC561\uC740 99,999P \uC774\uD558\uB9CC \uAC00\uB2A5\uD569\uB2C8\uB2E4.")
    private java.math.BigDecimal betAmount;

    private Boolean isPrivate;

    @Length(max = 4)
    @Pattern(regexp = "^\\d{4}$", message = "\uBE44\uBC00\uBC88\uD638\uB294 \uC22B\uC790 4\uC790\uB9AC\uC5EC\uC57C \uD569\uB2C8\uB2E4.")
    private String newPassword;
}
