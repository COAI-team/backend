package kr.or.kosa.backend.user.service;

import kr.or.kosa.backend.exception.CustomException;
import kr.or.kosa.backend.exception.ErrorCode;
import kr.or.kosa.backend.security.jwt.JwtProvider;
import kr.or.kosa.backend.user.domain.User;
import kr.or.kosa.backend.user.dto.*;
import kr.or.kosa.backend.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    @Value("${file.upload-dir}")
    private String uploadDir;

    private static final long REFRESH_TOKEN_EXPIRE_DAYS = 14;

    @Override
    public int register(UserRegisterRequestDto dto, MultipartFile imageFile) {

        if (!uploadDir.endsWith("/")) {
            uploadDir = uploadDir + "/";
        }

        if (!emailVerificationService.isVerified(dto.getEmail())) {
            throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        if (userMapper.findByEmail(dto.getEmail()) != null) {
            throw new CustomException(ErrorCode.EMAIL_DUPLICATE);
        }

        if (userMapper.findByNickname(dto.getNickname()) != null) {
            throw new CustomException(ErrorCode.NICKNAME_DUPLICATE);
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

        String imageUrl = handleUserImageUpload(dto.getNickname(), imageFile);
        userMapper.updateUserImage(userId, imageUrl);

        return userId;
    }

    private String handleUserImageUpload(String nickname, MultipartFile imageFile) {
        if (imageFile == null || imageFile.isEmpty()) {
            return "/profile-images/default.png";
        }

        if (imageFile.getSize() > 5 * 1024 * 1024) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_SIZE);
        }

        String contentType = imageFile.getContentType();
        if (contentType == null ||
                !(contentType.equals("image/jpeg") || contentType.equals("image/png"))) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_EXTENSION);
        }

        try {
            if (ImageIO.read(imageFile.getInputStream()) == null) {
                throw new CustomException(ErrorCode.INVALID_IMAGE_FILE);
            }
        } catch (IOException e) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_FILE);
        }

        String safeNickname = nickname.replaceAll("[^a-zA-Z0-9가-힣_\\-]", "_");
        String userFolder = uploadDir + safeNickname + "/profile/";

        File folder = new File(userFolder);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new CustomException(ErrorCode.FILE_SAVE_ERROR);
        }

        String fileName = UUID.randomUUID().toString() + "_" + imageFile.getOriginalFilename();
        Path filePath = Paths.get(userFolder + fileName);

        try {
            Files.copy(imageFile.getInputStream(), filePath);
        } catch (IOException e) {
            log.error("프로필 이미지 저장 실패", e);
            throw new CustomException(ErrorCode.FILE_SAVE_ERROR);
        }

        return "/profile-images/" + safeNickname + "/profile/" + fileName;
    }

    @Override
    public UserLoginResponseDto login(UserLoginRequestDto dto) {
        User user = userMapper.findByEmail(dto.getEmail());

        if (user == null) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }

        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtProvider.createRefreshToken(user.getId(), user.getEmail());

        // 🔥 RefreshToken을 Redis에 저장 (DB 대신)
        String refreshKey = "auth:refresh:" + user.getId();
        redisTemplate.opsForValue().set(
                refreshKey,
                refreshToken,
                REFRESH_TOKEN_EXPIRE_DAYS,
                TimeUnit.DAYS
        );

        return UserLoginResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(toResponseDto(user))
                .build();
    }

    @Override
    public String refresh(String bearerToken) {
        String refreshToken = bearerToken.replace("Bearer ", "");

        if (!jwtProvider.validateToken(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        Integer userId = jwtProvider.getUserId(refreshToken);

        String refreshKey = "auth:refresh:" + userId;
        String savedToken = redisTemplate.opsForValue().get(refreshKey);

        if (savedToken == null || !savedToken.equals(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
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
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        return toResponseDto(user);
    }

    private UserResponseDto toResponseDto(User user) {
        UserResponseDto dto = new UserResponseDto();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setName(user.getName());
        dto.setNickname(user.getNickname());
        dto.setImage(user.getImage());
        dto.setGrade(user.getGrade());
        dto.setRole(user.getRole());
        dto.setEnabled(user.getEnabled());
        return dto;
    }

    @Override
    public void sendPasswordResetLink(String email) {

        User user = userMapper.findByEmail(email);
        if (user == null) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
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
            throw new CustomException(ErrorCode.INVALID_OR_EXPIRED_TOKEN);
        }

        User user = userMapper.findByEmail(email);
        if (user == null) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        // 비밀번호 암호화 후 저장
        String encPassword = passwordEncoder.encode(dto.getNewPassword());
        userMapper.updatePassword(user.getId(), encPassword);

        // 토큰 삭제
        redisTemplate.delete(redisKey);
    }
}