package kr.or.kosa.backend.users.service;

import kr.or.kosa.backend.auth.github.dto.*;
import kr.or.kosa.backend.users.dto.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface UserService {

    Long register(UserRegisterRequestDto dto, MultipartFile imageFile);

    UserLoginResponseDto login(UserLoginRequestDto dto);

    String refresh(String token);

    boolean logout(String token);

    String sendPasswordResetLink(String email);

    boolean isResetTokenValid(String token);

    boolean resetPassword(String token, String newPassword);

    UserResponseDto updateUserInfo(Long userId, UserUpdateRequestDto dto, MultipartFile image);

    UserResponseDto getUserInfo(Long userId);

    boolean requestDelete(Long userId);

    boolean restoreUser(Long userId);

    GithubLoginResult githubLogin(GitHubUserResponse gitHubUser, boolean linkMode);

    boolean disconnectGithub(Long userId);

    boolean isGithubLinked(Long userId);

    Map<String, Object> getGithubUserInfo(Long userId);

    boolean linkGithubAccount(Long currentUserId, GitHubUserResponse gitHubUser);
}
