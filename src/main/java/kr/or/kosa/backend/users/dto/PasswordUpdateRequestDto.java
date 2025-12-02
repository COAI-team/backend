package kr.or.kosa.backend.users.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordUpdateRequestDto {
    private String currentUserPw;
    private String newUserPw;
}