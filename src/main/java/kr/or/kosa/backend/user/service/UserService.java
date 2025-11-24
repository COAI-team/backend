package kr.or.kosa.backend.user.service;

import kr.or.kosa.backend.user.dto.*;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    int register(UserRegisterRequestDto dto, MultipartFile imageFile);

    UserLoginResponseDto login(UserLoginRequestDto dto);

    String refresh(String token);

    void logout(String token);

    UserResponseDto getById(Integer id);

    void sendPasswordResetLink(String email);

    void resetPassword(PasswordResetConfirmDto dto);
}

