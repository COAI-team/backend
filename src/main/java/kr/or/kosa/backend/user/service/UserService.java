package kr.or.kosa.backend.user.service;

import kr.or.kosa.backend.user.dto.*;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    int register(UserRegisterRequestDto dto, MultipartFile imageFile);

    UserLoginResponseDto login(UserLoginRequestDto dto);

    String refresh(String token);

    boolean logout(String token);

    String sendPasswordResetLink(String email);

    boolean isResetTokenValid(String token);

    boolean resetPassword(String token, String newPassword);

    boolean updatePassword(Integer userId, PasswordUpdateRequestDto dto);

    UserResponseDto updateUserInfo(Integer userId, UserUpdateRequestDto dto, MultipartFile image);

    String updateEmail(Integer userId, String newEmail);

    UserResponseDto getUserInfo(Integer userId);
}