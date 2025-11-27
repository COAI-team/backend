package kr.or.kosa.backend.user.service;

import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import kr.or.kosa.backend.infra.s3.S3Uploader;
import kr.or.kosa.backend.security.jwt.JwtProvider;
import kr.or.kosa.backend.user.domain.User;
import kr.or.kosa.backend.user.dto.*;
import kr.or.kosa.backend.user.exception.UserErrorCode;
import kr.or.kosa.backend.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;
    private final S3Uploader s3Uploader;
    private final PasswordResetTokenService passwordResetTokenService;

    private static final long REFRESH_TOKEN_EXPIRE_DAYS = 14;
    private static final String REFRESH_KEY_PREFIX = "auth:refresh:";
    private static final String BLACKLIST_KEY_PREFIX = "auth:blacklist:";

    // ---------------------------------------------------------
    // 회원가입
    // ---------------------------------------------------------
    @Override
    public int register(UserRegisterRequestDto dto, MultipartFile imageFile) {

        if (!emailVerificationService.isVerified(dto.getEmail())) {
            throw new CustomBusinessException(UserErrorCode.EMAIL_NOT_VERIFIED);
        }

        if (userMapper.findByEmail(dto.getEmail()) != null) {
            throw new CustomBusinessException(UserErrorCode.EMAIL_DUPLICATE);
        }

        if (userMapper.findByNickname(dto.getNickname()) != null) {
            throw new CustomBusinessException(UserErrorCode.NICKNAME_DUPLICATE);
        }

        User user = new User();
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setName(dto.getName());
        user.setNickname(dto.getNickname());
        user.setImage(null);
        user.setEnabled(true);

        int result = userMapper.insertUser(user);
        if (result <= 0) {
            throw new CustomBusinessException(UserErrorCode.USER_CREATE_FAIL);
        }

        int userId = user.getId();

        // 프로필 이미지 처리
        String imageUrl;
        if (imageFile != null && !imageFile.isEmpty()) {
            String folderPath = "profile-images/" + dto.getNickname() + "/profile";
            try {
                imageUrl = s3Uploader.upload(imageFile, folderPath);
            } catch (IOException e) {
                throw new CustomBusinessException(UserErrorCode.FILE_SAVE_ERROR);
            }
        } else {
            imageUrl = "https://codenemsy.s3.ap-northeast-2.amazonaws.com/profile-images/default.png";
        }

        userMapper.updateUserImage(userId, imageUrl);
        return userId;
    }

    // ---------------------------------------------------------
    // 로그인
    // ---------------------------------------------------------
    @Override
    public UserLoginResponseDto login(UserLoginRequestDto dto) {

        User user = userMapper.findByEmail(dto.getEmail());
        if (user == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new CustomBusinessException(UserErrorCode.INVALID_PASSWORD);
        }

        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtProvider.createRefreshToken(user.getId(), user.getEmail());

        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + user.getId(),
                refreshToken,
                REFRESH_TOKEN_EXPIRE_DAYS,
                TimeUnit.DAYS
        );

        UserResponseDto userDto = UserResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .nickname(user.getNickname())
                .image(user.getImage())
                .grade(user.getGrade())
                .role(user.getRole())
                .enabled(user.getEnabled())
                .build();

        return UserLoginResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(userDto)
                .build();
    }

    // ---------------------------------------------------------
    // Access Token 재발급
    // ---------------------------------------------------------
    @Override
    public String refresh(String bearerToken) {
        String refreshToken = bearerToken.replace("Bearer ", "");

        if (!jwtProvider.validateToken(refreshToken)) {
            throw new CustomBusinessException(UserErrorCode.INVALID_TOKEN);
        }

        Integer userId = jwtProvider.getUserId(refreshToken);

        String savedToken = redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + userId);
        if (savedToken == null || !savedToken.equals(refreshToken)) {
            throw new CustomBusinessException(UserErrorCode.INVALID_TOKEN);
        }

        return jwtProvider.createAccessToken(userId, jwtProvider.getEmail(refreshToken));
    }

    // ---------------------------------------------------------
    // 로그아웃
    // ---------------------------------------------------------
    @Override
    public boolean logout(String bearerToken) {

        try {
            String token = bearerToken.replace("Bearer ", "");

            if (!jwtProvider.validateToken(token)) {
                return false;
            }

            Integer userId = jwtProvider.getUserId(token);

            // refresh 삭제
            redisTemplate.delete(REFRESH_KEY_PREFIX + userId);

            long expireAt = jwtProvider.getTokenRemainingTime(token);

            if (expireAt > 0) {
                redisTemplate.opsForValue().set(
                        BLACKLIST_KEY_PREFIX + token,
                        "logout",
                        expireAt,
                        TimeUnit.MILLISECONDS
                );
            }

            return true;

        } catch (Exception e) {
            log.error("Logout failed: {}", e.getMessage());
            return false;
        }
    }

    // ---------------------------------------------------------
    // 비밀번호 재설정 이메일 요청
    // ---------------------------------------------------------
    @Override
    public String sendPasswordResetLink(String email) {

        User user = userMapper.findByEmail(email);
        if (user == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        String token = passwordResetTokenService.createResetToken(user.getId());
        String resetUrl = "http://localhost:5173/reset-password?token=" + token;

        boolean sent = emailVerificationService.send(
                email,
                "[서비스명] 비밀번호 재설정 안내",
                "아래 링크를 클릭하여 비밀번호를 재설정하세요.\n\n" +
                        resetUrl + "\n\n" +
                        "본 링크는 15분 동안 유효합니다."
        );

        if (!sent) {
            throw new CustomBusinessException(UserErrorCode.EMAIL_SEND_FAIL);
        }

        return "비밀번호 재설정 이메일이 발송되었습니다.";
    }

    // ---------------------------------------------------------
    // 비밀번호 재설정
    // ---------------------------------------------------------
    @Override
    public boolean resetPassword(String token, String newPassword) {

        Integer userId = passwordResetTokenService.validateToken(token);
        if (userId == null) return false;

        String encryptedPassword = passwordEncoder.encode(newPassword);

        int result = userMapper.updatePassword(userId, encryptedPassword);
        if (result > 0) {

            boolean deleted = passwordResetTokenService.deleteToken(token);
            if (!deleted) {
                log.warn("Reset token deletion failed: {}", token);
            }

            return true;
        }

        return false;
    }

    // ---------------------------------------------------------
    // 로그인 상태에서 비밀번호 변경
    // ---------------------------------------------------------
    @Override
    public boolean updatePassword(Integer userId, PasswordUpdateRequestDto dto) {

        User user = userMapper.findById(userId);
        if (user == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            return false;
        }

        int result = userMapper.updatePassword(userId, passwordEncoder.encode(dto.getNewPassword()));
        return result > 0;
    }

    // ---------------------------------------------------------
    // 사용자 정보 수정
    // ---------------------------------------------------------
    @Override
    public UserResponseDto updateUserInfo(Integer userId, UserUpdateRequestDto dto, MultipartFile image) {

        User user = userMapper.findById(userId);
        if (user == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        // 닉네임 중복 검사
        if (dto.getNickname() != null && !dto.getNickname().isBlank()) {
            User existingUser = userMapper.findByNickname(dto.getNickname());
            if (existingUser != null && !existingUser.getId().equals(userId)) {
                throw new CustomBusinessException(UserErrorCode.NICKNAME_DUPLICATE);
            }
        }

        int updated = userMapper.updateUserInfo(userId, dto.getName(), dto.getNickname());
        if (updated <= 0) {
            throw new CustomBusinessException(UserErrorCode.UPDATE_FAIL);
        }

        // 이미지 업데이트
        if (image != null && !image.isEmpty()) {
            try {
                String imageUrl = s3Uploader.upload(image, "profile-images/" + userId + "/profile");
                userMapper.updateUserImage(userId, imageUrl);
            } catch (IOException e) {
                throw new CustomBusinessException(UserErrorCode.FILE_SAVE_ERROR);
            }
        }

        User updatedUser = userMapper.findById(userId);

        return UserResponseDto.builder()
                .id(updatedUser.getId())
                .email(updatedUser.getEmail())
                .name(updatedUser.getName())
                .nickname(updatedUser.getNickname())
                .image(updatedUser.getImage())
                .grade(updatedUser.getGrade())
                .role(updatedUser.getRole())
                .enabled(updatedUser.getEnabled())
                .build();
    }

    // ---------------------------------------------------------
    // 이메일 수정
    // ---------------------------------------------------------
    @Override
    public String updateEmail(Integer userId, String newEmail) {

        User user = userMapper.findById(userId);
        if (user == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        if (emailVerificationService.isVerified(newEmail)) {
            throw new CustomBusinessException(UserErrorCode.EMAIL_NOT_VERIFIED);
        }

        User existingUser = userMapper.findByEmail(newEmail);
        if (existingUser != null && !existingUser.getId().equals(userId)) {
            throw new CustomBusinessException(UserErrorCode.EMAIL_DUPLICATE);
        }

        int result = userMapper.updateUserEmail(userId, newEmail);
        if (result <= 0) {
            throw new CustomBusinessException(UserErrorCode.UPDATE_FAIL);
        }

        boolean cleared = emailVerificationService.clearVerification(newEmail);
        if (!cleared) {
            log.warn("Failed to clear email verification for {}", newEmail);
        }

        return newEmail;
    }

    // ---------------------------------------------------------
    // 재설정 토큰 유효성 확인
    // ---------------------------------------------------------
    @Override
    public boolean isResetTokenValid(String token) {
        return passwordResetTokenService.validateToken(token) != null;
    }

    // ---------------------------------------------------------
    // 내 정보 조회
    // ---------------------------------------------------------
    @Override
    public UserResponseDto getUserInfo(Integer userId) {

        User user = userMapper.findById(userId);

        if (user == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        return UserResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .nickname(user.getNickname())
                .image(user.getImage())
                .grade(user.getGrade())
                .role(user.getRole())
                .enabled(user.getEnabled())
                .build();
    }

    // ============================================================
    // 90일 뒤 탈퇴 예약
    // ============================================================
    @Override
    public boolean requestDelete(Integer userId) {

        User user = userMapper.findById(userId);
        if (user == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        // 이미 탈퇴 예약 중인지 확인
        boolean isAlreadyScheduled =
                user.getDeletedAt() != null &&
                        !Boolean.TRUE.equals(user.getIsDeleted()); // 🔥 핵심 수정

        if (isAlreadyScheduled) {
            throw new CustomBusinessException(UserErrorCode.ALREADY_SCHEDULED_DELETE);
        }

        // 90일 뒤 탈퇴될 예정
        LocalDateTime deletedAt = LocalDateTime.now().plusDays(90);

        int result = userMapper.scheduleDelete(userId, deletedAt);

        return result > 0;
    }

    // ============================================================
    // 탈퇴 신청 복구
    // ============================================================
    @Override
    public boolean restoreUser(Integer userId) {

        User user = userMapper.findById(userId);
        if (user == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        // 탈퇴 예약조차 되어있지 않으면 복구 불가
        if (user.getDeletedAt() == null) {
            return false;
        }

        // 이미 90일이 지나 실제 삭제가 예정된 계정
        if (user.getDeletedAt().isBefore(LocalDateTime.now())) {
            return false;
        }

        int result = userMapper.restoreUser(userId);
        return result > 0;
    }
}