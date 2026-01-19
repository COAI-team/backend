package kr.or.kosa.backend.auth.github.dto;

import kr.or.kosa.backend.users.dto.UserLoginResponseDto;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GitHubCallbackResponse {

    private boolean linkMode;               // 프론트에서 "연동 화면" 띄울지 여부
    private boolean needLink;               // 기존 이메일 계정 존재 → 계정 통합 필요
    private Long userId;                    // 연동할 기존 계정 ID
    private String message;                 // 모달에 표시할 메시지

    private GitHubUserResponse gitHubUser;  // GitHub 사용자 정보
    private UserLoginResponseDto loginResponse; // OAuth 로그인 성공 시 (토큰 + user)

    private String accessToken;
    private String refreshToken;
}