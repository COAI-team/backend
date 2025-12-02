package kr.or.kosa.backend.admin.dto.response;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class UserFindResponseDto{
    private long userId;
    private String userEmail;
    private String userNickName;
    private int userGrade;
    private String userRole;
}
