package kr.or.kosa.backend.battle.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
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
    @Length(max = 100)
    private String title;

    @NotNull
    private Long algoProblemId;

    @NotNull
    private Long languageId;

    @NotBlank(message = "levelMode는 필수입니다.")
    private String levelMode;

    @PositiveOrZero
    private Integer maxDurationMinutes;

    @DecimalMin(value = "0", inclusive = true, message = "베팅 금액은 0 이상이어야 합니다.")
    @PositiveOrZero
    private BigDecimal betAmount = BigDecimal.ZERO;

    @Length(max = 4)
    @Pattern(regexp = "^\\d{4}$", message = "비밀번호는 숫자 4자리여야 합니다.", groups = OptionalPassword.class)
    private String password; // optional 4-digit numeric

    public interface OptionalPassword {}
}
