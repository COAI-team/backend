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

    // ⭐ 새로 추가
    private final PasswordResetTokenService passwordResetTokenService;

    private static final long REFRESH_TOKEN_EXPIRE_DAYS = 14;
    private static final String REFRESH_KEY_PREFIX = "auth:refresh:";
    private static final String BLACKLIST_KEY_PREFIX = "auth:blacklist:";

    // -----------------------------
    // 회원가입
    // -----------------------------
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

        userMapper.insertUser(user);
        int userId = user.getId();

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

    // -----------------------------
    // 로그인
    // -----------------------------
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

    // -----------------------------
    // Access Token 재발급
    // -----------------------------
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

    // -----------------------------
    // 로그아웃
    // -----------------------------
    @Override
    public void logout(String bearerToken) {
        String token = bearerToken.replace("Bearer ", "");

        if (!jwtProvider.validateToken(token)) return;

        Integer userId = jwtProvider.getUserId(token);

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
    }

    // ============================================================================
    // 비밀번호 재설정 (토큰 기반 도입)
    // ============================================================================

    /**
     * 비밀번호 재설정 이메일 발송
     */
    @Override
    public void sendPasswordResetLink(String email) {

        User user = userMapper.findByEmail(email);
        if (user == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        // 1) 토큰 생성
        String token = passwordResetTokenService.createResetToken(user.getId());

        // 2) 이메일 전송
        String resetUrl = "http://localhost:5173/reset-password?token=" + token;

        emailVerificationService.send(
                email,
                "[서비스명] 비밀번호 재설정 안내",
                "아래 링크를 클릭하여 비밀번호를 재설정하세요.\n\n" +
                        resetUrl + "\n\n" +
                        "본 링크는 15분 동안 유효합니다."
        );
    }

    /**
     * 토큰 유효성 체크
     */
    @Override
    public boolean isResetTokenValid(String token) {
        return passwordResetTokenService.validateToken(token) != null;
    }

    /**
     * 새 비밀번호 설정 (토큰 기반)
     */
    @Override
    public void resetPassword(String token, String newPassword) {

        Integer userId = passwordResetTokenService.validateToken(token);
        if (userId == null) {
            throw new CustomBusinessException(UserErrorCode.INVALID_OR_EXPIRED_TOKEN);
        }

        User user = userMapper.findById(userId);
        if (user == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        String encryptedPassword = passwordEncoder.encode(newPassword);

        userMapper.updatePassword(userId, encryptedPassword);

        // 1회성 → 삭제
        passwordResetTokenService.deleteToken(token);
    }

    // -----------------------------
    // 로그인된 사용자 비밀번호 변경
    // -----------------------------
    @Override
    public void updatePassword(Integer userId, PasswordUpdateRequestDto dto) {

        User user = userMapper.findById(userId);
        if (user == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            throw new CustomBusinessException(UserErrorCode.INVALID_PASSWORD);
        }

        String encryptedNewPassword = passwordEncoder.encode(dto.getNewPassword());
        userMapper.updatePassword(userId, encryptedNewPassword);
    }
}