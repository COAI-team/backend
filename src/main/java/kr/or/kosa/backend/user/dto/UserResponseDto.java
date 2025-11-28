package kr.or.kosa.backend.user.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserResponseDto {
    private Long id;
    private String email;
    private String name;
    private String nickname;
    private String image;
    private Integer grade;
    private String role;
    private Boolean enabled;
}