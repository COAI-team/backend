package kr.or.kosa.backend.battle.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.Length;

@Getter
@Setter
public class BattleRoomCreateRequest {

    @NotBlank
    @Length(max = 50)
    private String title;

    private Long algoProblemId;

    @NotNull
    private Long languageId;

    @NotBlank(message = "\uB808\uBCA8 \uB9E4\uCE6D \uADDC\uCE59\uC740 \uD544\uC218\uC785\uB2C8\uB2E4.")
    private String levelMode;

    @PositiveOrZero
    @Max(120)
    @Min(1)
    private Integer maxDurationMinutes;

    @DecimalMin(value = "0", inclusive = true, message = "\uBCA0\uD305 \uAE08\uC561\uC740 0 \uC774\uC0C1\uC774\uC5B4\uC57C \uD569\uB2C8\uB2E4.")
    @jakarta.validation.constraints.DecimalMax(value = "99999", message = "\uBCA0\uD305 \uAE08\uC561\uC740 99,999P \uC774\uD558\uB9CC \uAC00\uB2A5\uD569\uB2C8\uB2E4.")
    @PositiveOrZero
    private BigDecimal betAmount = BigDecimal.ZERO;

    @Length(max = 4)
    @Pattern(regexp = "^\\d{4}$", message = "\uBE44\uBC00\uBC88\uD638\uB294 \uC22B\uC790 4\uC790\uB9AC\uC5EC\uC57C \uD569\uB2C8\uB2E4.", groups = OptionalPassword.class)
    private String password; // optional 4-digit numeric

    public interface OptionalPassword {}
}
