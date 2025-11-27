package kr.or.kosa.backend.user.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UserUpdateRequestDto {
    private String name;        // USER_NAME
    private String nickname;    // USER_NICKNAME
}