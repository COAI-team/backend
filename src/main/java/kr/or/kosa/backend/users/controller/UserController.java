package kr.or.kosa.backend.users.controller;

import jakarta.validation.Valid;
import kr.or.kosa.backend.auth.github.dto.GitHubUserResponse;
import kr.or.kosa.backend.security.jwt.JwtUserDetails;
import kr.or.kosa.backend.users.dto.*;
import kr.or.kosa.backend.users.service.UserService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    private static final ResponseEntity<UserResponseDto> UNAUTHORIZED =
            ResponseEntity.status(401).build();

    /**
     * 회원가입
     */
    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RegisterResponse> register(
            @Valid @ModelAttribute UserRegisterRequestDto dto,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        Long userId = userService.register(dto, image);
        return ResponseEntity.ok(RegisterResponse.builder()
                .success(true)
                .message("회원가입이 완료되었습니다.")
                .userId(userId)
                .build());
    }

    /**
     * 로그인
     */
    @PostMapping("/login")
    public ResponseEntity<UserLoginResponseDto> login(@RequestBody UserLoginRequestDto dto) {
        return ResponseEntity.ok(userService.login(dto));
    }

    /**
     * AccessToken 재발급
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestHeader("Authorization") String token) {
        String newAccessToken = userService.refresh(token);
        return ResponseEntity.ok(TokenResponse.builder()
                .success(true)
                .accessToken(newAccessToken)
                .build());
    }

    /**
     * 로그아웃
     */
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(@RequestHeader("Authorization") String token) {
        boolean result = userService.logout(token);
        return ResponseEntity.ok(MessageResponse.builder()
                .success(result)
                .message(result ? "로그아웃 완료" : "로그아웃 실패")
                .build());
    }

    /**
     * 비밀번호 재설정 이메일 요청
     */
    @PostMapping("/password/reset/request")
    public ResponseEntity<MessageResponse> requestPasswordReset(@RequestBody Map<String, String> body) {
        String message = userService.sendPasswordResetLink(body.get("email"));
        return ResponseEntity.ok(MessageResponse.builder()
                .success(true)
                .message(message)
                .build());
    }

    /**
     * 비밀번호 재설정 토큰 유효성 검증
     */
    @GetMapping("/password/reset/validate")
    public ResponseEntity<MessageResponse> validateResetToken(@RequestParam String token) {
        boolean valid = userService.isResetTokenValid(token);
        return ResponseEntity.ok(MessageResponse.builder()
                .success(valid)
                .message(valid ? "유효한 토큰입니다." : "토큰이 만료되었거나 잘못되었습니다.")
                .build());
    }

    /**
     * 새 비밀번호 설정
     */
    @PostMapping("/password/reset/confirm")
    public ResponseEntity<MessageResponse> confirmPasswordReset(
            @RequestBody PasswordResetConfirmRequest dto
    ) {
        boolean result = userService.resetPassword(dto.getToken(), dto.getNewUserPw());
        return ResponseEntity.ok(MessageResponse.builder()
                .success(result)
                .message(result ? "비밀번호가 성공적으로 변경되었습니다."
                        : "유효하지 않은 토큰이거나 만료되었습니다.")
                .build());
    }

    /**
     * 내 정보 조회
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> getMyInfo(
            @AuthenticationPrincipal(expression = "details") JwtUserDetails user
    ) {
        if (user == null) {
            return UNAUTHORIZED;
        }
        UserResponseDto dto = userService.getUserInfo(user.id());
        return ResponseEntity.ok(dto);
    }

    /**
     * 일반 정보 수정 (이름 / 닉네임 / 프로필 사진)
     */
    @PutMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserUpdateResponse> updateMyInfo(
            @AuthenticationPrincipal JwtUserDetails user,
            @ModelAttribute UserUpdateRequestDto dto,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        UserResponseDto updated = userService.updateUserInfo(user.id(), dto, image);
        return ResponseEntity.ok(UserUpdateResponse.builder()
                .success(true)
                .message("회원 정보가 수정되었습니다.")
                .user(updated)
                .build());
    }

    /**
     * 탈퇴 신청 (90일 뒤 삭제)
     */
    @DeleteMapping("/me")
    public ResponseEntity<MessageResponse> requestDelete(
            @AuthenticationPrincipal JwtUserDetails user
    ) {
        boolean result = userService.requestDelete(user.id());
        return ResponseEntity.ok(MessageResponse.builder()
                .success(result)
                .message(result
                        ? "탈퇴 신청이 완료되었습니다. 90일 내 복구 가능합니다."
                        : "탈퇴 신청에 실패했습니다.")
                .build());
    }

    /**
     * 탈퇴 복구
     */
    @PutMapping("/me/restore")
    public ResponseEntity<MessageResponse> restoreUser(
            @AuthenticationPrincipal JwtUserDetails user
    ) {
        boolean result = userService.restoreUser(user.id());
        return ResponseEntity.ok(MessageResponse.builder()
                .success(result)
                .message(result
                        ? "계정 복구가 완료되었습니다."
                        : "복구할 수 없는 계정이거나 이미 삭제 처리되었습니다.")
                .build());
    }

    /**
     * GitHub 계정을 현재 사용자 계정에 연동
     */
    @PostMapping("/github/link")
    public ResponseEntity<MessageResponse> linkGithub(
            @AuthenticationPrincipal JwtUserDetails user,
            @RequestBody GitHubUserResponse gitHubUser
    ) {
        System.out.println("깃 허브 링크 컨트롤러 111  ====>> "  + user.getUsername()+ "=====" + user.getDetails().id() + "====="+ user.getDetails().toString());
        System.out.println("깃 허브 링크 컨트롤러 2222  ====>> "  + gitHubUser.getEmail()+ "=====" + gitHubUser.getEmail());
        boolean result = userService.linkGithubAccount(user.id(), gitHubUser);
        return ResponseEntity.ok(MessageResponse.builder()
                .success(result)
                .message(result ? "GitHub 계정이 연동되었습니다." : "GitHub 연동에 실패했습니다.")
                .build());
    }
}