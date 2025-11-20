package kr.or.kosa.backend.user.dto;

import lombok.Data;

@Data
public class UserResponseDto {
    private Integer id;
    private String email;
    private String name;
    private String nickname;
    private String image;
    private Integer grade;
    private String role;
    private Boolean enabled;
}