package kr.or.kosa.backend.users.domain;

import kr.or.kosa.backend.users.dto.UserResponseDto;
import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Users {

    private Long userId;
    private String userEmail;
    private String userPw;
    private String userName;
    private String userNickname;
    private String userImage;
    private Integer userGrade;
    private String userRole;        // ENUM → String으로 매핑
    private Boolean userIsdeleted;
    private LocalDateTime userDeletedat;
    private LocalDateTime userCreatedat;
    private LocalDateTime userUpdatedat;
    private Boolean userEnabled;
    private Boolean userIssubscribed;

    private Boolean needLink = false;

    public UserResponseDto toDto() {
        return UserResponseDto.builder()
                .userId(this.userId)
                .userEmail(this.userEmail)
                .userName(this.userName)
                .userNickname(this.userNickname)
                .userImage(this.userImage)
                .userGrade(this.userGrade)
                .userRole(this.userRole)
                .userEnabled(this.userEnabled)
                .build();
    }
}