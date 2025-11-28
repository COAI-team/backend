package kr.or.kosa.backend.user.service;

import kr.or.kosa.backend.user.dto.*;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    Long register(UserRegisterRequestDto dto, MultipartFile imageFile);

    UserLoginResponseDto login(UserLoginRequestDto dto);

    String refresh(String token);

    boolean logout(String token);

    String sendPasswordResetLink(String email);

    boolean isResetTokenValid(String token);

    boolean resetPassword(String token, String newPassword);

    boolean updatePassword(Long userId, PasswordUpdateRequestDto dto);

    UserResponseDto updateUserInfo(Long userId, UserUpdateRequestDto dto, MultipartFile image);

    String updateEmail(Long userId, String newEmail);

    UserResponseDto getUserInfo(Long userId);

    boolean requestDelete(Long userId);

    boolean restoreUser(Long userId);
}
