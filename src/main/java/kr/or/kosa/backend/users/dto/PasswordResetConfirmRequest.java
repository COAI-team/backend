package kr.or.kosa.backend.users.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class PasswordResetConfirmRequest {

    // 비밀번호 재설정 토큰 (이게 없어서 오류 발생)
    private String token;

    // 새 비밀번호
    private String newUserPw;
}