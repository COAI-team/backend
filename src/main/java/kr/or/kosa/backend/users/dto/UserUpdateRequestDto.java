package kr.or.kosa.backend.users.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserUpdateRequestDto {
    private String userName;
    private String userNickname;
    private String githubId;
    private String githubToken;
}