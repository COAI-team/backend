package kr.or.kosa.backend.users.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserResponseDto {
    private Long userId;
    private String userEmail;
    private String userName;
    private String userNickname;
    private String userImage;
    private Integer userGrade;
    private String userRole;
    private Boolean userEnabled;
    private Boolean userIssubscribed;  // 스키마에 존재하므로 반드시 포함
}