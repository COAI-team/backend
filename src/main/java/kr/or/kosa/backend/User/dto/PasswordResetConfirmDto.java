package kr.or.kosa.backend.user.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordResetConfirmDto {
    private String token;
    private String newPassword;
}