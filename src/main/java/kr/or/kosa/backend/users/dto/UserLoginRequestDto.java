package kr.or.kosa.backend.users.dto;

import lombok.Data;

@Data
public class UserLoginRequestDto {
    private String userEmail;
    private String userPw;
}