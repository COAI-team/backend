package kr.or.kosa.backend.auth.github.dto;

import kr.or.kosa.backend.users.domain.Users;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GithubLoginResult {

    private Users user;        // 로그인 or 연동 대상 사용자
    private boolean needLink;  // 연동 필요 여부

    private String accessToken;
    private String refreshToken;
}