package kr.or.kosa.backend.user.service;

import kr.or.kosa.backend.user.dto.*;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    int register(UserRegisterRequestDto dto, MultipartFile imageFile);

    UserLoginResponseDto login(UserLoginRequestDto dto);

    String refresh(String token);

    void logout(String token);

    /**
     * 비밀번호 재설정 이메일 요청 (토큰 발송)
     */
    void sendPasswordResetLink(String email);

    /**
     * 토큰 유효성 검증
     */
    boolean isResetTokenValid(String token);

    /**
     * 토큰 기반 비밀번호 재설정
     */
    void resetPassword(String token, String newPassword);

    /**
     * 로그인 상태에서 비밀번호 변경
     */
    void updatePassword(Integer userId, PasswordUpdateRequestDto dto);
}