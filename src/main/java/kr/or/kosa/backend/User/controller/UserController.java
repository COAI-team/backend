package kr.or.kosa.backend.user.controller;

import jakarta.validation.Valid;
import kr.or.kosa.backend.user.dto.*;
import kr.or.kosa.backend.user.service.UserService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    
    private static final String KEY_SUCCESS = "success";
    private static final String KEY_MESSAGE = "message";

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> register(
            @Valid @ModelAttribute UserRegisterRequestDto dto,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        int userId = userService.register(dto, image);

        return ResponseEntity.ok(Map.of(
                KEY_SUCCESS, true,
                KEY_MESSAGE, "회원가입이 완료되었습니다.",
                "userId", userId
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<UserLoginResponseDto> login(@RequestBody UserLoginRequestDto dto) {
        UserLoginResponseDto response = userService.login(dto);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(@RequestHeader("Authorization") String token) {
        String newAccessToken = userService.refresh(token);
        return ResponseEntity.ok(Map.of("accessToken", newAccessToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(@RequestHeader("Authorization") String token) {
        userService.logout(token);
        return ResponseEntity.ok(Map.of(
                KEY_SUCCESS, true,
                KEY_MESSAGE, "로그아웃 완료"
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDto> getUser(@PathVariable Integer id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    @PostMapping("/password/reset/request")
    public ResponseEntity<Map<String, Boolean>> requestPasswordReset(@RequestBody Map<String, String> body) {
        userService.sendPasswordResetLink(body.get("email"));
        return ResponseEntity.ok(Map.of(KEY_SUCCESS, true));
    }

    @PostMapping("/password/reset/confirm")
    public ResponseEntity<Map<String, Object>> confirmPasswordReset(@RequestBody PasswordResetConfirmDto dto) {
        userService.resetPassword(dto);
        return ResponseEntity.ok(Map.of(
                KEY_SUCCESS, true,
                KEY_MESSAGE, "비밀번호가 변경되었습니다."
        ));
    }
}