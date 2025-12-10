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
    private Boolean userIssubscribed;
    private String githubId;
    private Boolean hasGithubToken;
}