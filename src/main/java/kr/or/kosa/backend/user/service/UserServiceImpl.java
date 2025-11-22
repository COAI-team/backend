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
import java.util.UUID;
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

    private static final long REFRESH_TOKEN_EXPIRE_DAYS = 14;

    @Override
    public int register(UserRegisterRequestDto dto, MultipartFile imageFile) {

        // 1. 이메일 인증 확인
        if (!emailVerificationService.isVerified(dto.getEmail())) {
            throw new CustomBusinessException(UserErrorCode.EMAIL_NOT_VERIFIED);
        }

        // 2. 중복 체크
        if (userMapper.findByEmail(dto.getEmail()) != null) {
            throw new CustomBusinessException(UserErrorCode.EMAIL_DUPLICATE);
        }

        if (userMapper.findByNickname(dto.getNickname()) != null) {
            throw new CustomBusinessException(UserErrorCode.NICKNAME_DUPLICATE);
        }

        // 3. User 저장 (이미지 제외)
        User user = new User();
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setName(dto.getName());
        user.setNickname(dto.getNickname());
        user.setImage(null);
        user.setEnabled(true);

        userMapper.insertUser(user);
        int userId = user.getId();

        // 4. 프로필 이미지 S3 업로드
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

        // 5. DB에 이미지 URL 저장
        userMapper.updateUserImage(userId, imageUrl);

        return userId;
    }

    @Override
    public UserLoginResponseDto login(UserLoginRequestDto dto) {

        User user = userMapper.findByEmail(dto.getEmail());

        if (user == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new CustomBusinessException(UserErrorCode.INVALID_PASSWORD);
        }

        // 🔥 Base64 변환 제거 → S3 URL 그대로 사용
        String profileImageUrl = user.getImage();

        // 🔑 토큰 생성
        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtProvider.createRefreshToken(user.getId(), user.getEmail());

        // 💾 Redis에 RefreshToken 저장
        String refreshKey = "auth:refresh:" + user.getId();
        redisTemplate.opsForValue().set(
                refreshKey,
                refreshToken,
                REFRESH_TOKEN_EXPIRE_DAYS,
                TimeUnit.DAYS
        );

        // 🎯 User DTO 생성
        UserResponseDto userDto = UserResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .nickname(user.getNickname())
                .image(profileImageUrl)
                .grade(user.getGrade())
                .role(user.getRole())
                .enabled(user.getEnabled())
                .build();

        // 🎯 응답 반환
        return UserLoginResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(userDto)
                .build();
    }

    @Override
    public String refresh(String bearerToken) {
        String refreshToken = bearerToken.replace("Bearer ", "");

        if (!jwtProvider.validateToken(refreshToken)) {
            throw new CustomBusinessException(UserErrorCode.INVALID_TOKEN);
        }

        Integer userId = jwtProvider.getUserId(refreshToken);

        String refreshKey = "auth:refresh:" + userId;
        String savedToken = redisTemplate.opsForValue().get(refreshKey);

        if (savedToken == null || !savedToken.equals(refreshToken)) {
            throw new CustomBusinessException(UserErrorCode.INVALID_TOKEN);
        }

        return jwtProvider.createAccessToken(userId, jwtProvider.getEmail(refreshToken));
    }

    @Override
    public void logout(String bearerToken) {
        String token = bearerToken.replace("Bearer ", "");

        if (!jwtProvider.validateToken(token)) return;

        Integer userId = jwtProvider.getUserId(token);

        // 1) RefreshToken 삭제
        redisTemplate.delete("auth:refresh:" + userId);

        // 2) AccessToken 블랙리스트 처리
        long expireAt = jwtProvider.getTokenRemainingTime(token); // 토큰 남은 시간(ms)
        if (expireAt > 0) {
            String blacklistKey = "auth:blacklist:" + token;
            redisTemplate.opsForValue().set(blacklistKey, "logout", expireAt, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public UserResponseDto getById(Integer id) {
        User user = userMapper.findById(id);

        if (user == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        return toResponseDto(user);
    }

    public UserResponseDto toResponseDto(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .nickname(user.getNickname())
                .image(user.getImage())   // 원본 경로 그대로 사용
                .grade(user.getGrade())
                .role(user.getRole())
                .enabled(user.getEnabled())
                .build();
    }

    @Override
    public void sendPasswordResetLink(String email) {

        User user = userMapper.findByEmail(email);
        if (user == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        // 비밀번호 재설정 토큰 생성 (UUID)
        String token = UUID.randomUUID().toString();

        String redisKey = "reset:token:" + token;
        redisTemplate.opsForValue().set(redisKey, email, 30, TimeUnit.MINUTES);

        // 프론트엔드의 비밀번호 재설정 페이지 URL
        String resetLink = "https://your-frontend.com/reset-password?token=" + token;

        // 이메일 보내기
        emailVerificationService.send(
                email,
                "[서비스명] 비밀번호 재설정",
                "아래 링크를 클릭하여 비밀번호를 재설정하세요.\n" +
                        resetLink + "\n" +
                        "링크는 30분 동안만 유효합니다."
        );
    }

    @Override
    public void resetPassword(PasswordResetConfirmDto dto) {

        String redisKey = "reset:token:" + dto.getToken();
        String email = redisTemplate.opsForValue().get(redisKey);

        if (email == null) {
            throw new CustomBusinessException(UserErrorCode.INVALID_OR_EXPIRED_TOKEN);
        }

        User user = userMapper.findByEmail(email);
        if (user == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        // 비밀번호 암호화 후 저장
        String encPassword = passwordEncoder.encode(dto.getNewPassword());
        userMapper.updatePassword(user.getId(), encPassword);

        // 토큰 삭제
        redisTemplate.delete(redisKey);
    }
}