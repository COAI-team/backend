package kr.or.kosa.backend.users.service;

import kr.or.kosa.backend.auth.github.dto.GithubLoginResult;
import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import kr.or.kosa.backend.commons.util.EncryptionUtil; // Import added
import kr.or.kosa.backend.infra.s3.S3Uploader;
import kr.or.kosa.backend.security.jwt.JwtProvider;
import kr.or.kosa.backend.users.domain.Users;
import kr.or.kosa.backend.users.dto.*;
import kr.or.kosa.backend.users.exception.UserErrorCode;
import kr.or.kosa.backend.users.mapper.UserMapper;
import kr.or.kosa.backend.tutor.subscription.SubscriptionTier;
import kr.or.kosa.backend.tutor.subscription.SubscriptionTierResolver;
import kr.or.kosa.backend.auth.github.dto.GitHubUserResponse;
import org.springframework.beans.factory.annotation.Value;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;
    private final S3Uploader s3Uploader;
    private final PasswordResetTokenService passwordResetTokenService;
    private final EncryptionUtil encryptionUtil; // Injected
    private final SubscriptionTierResolver subscriptionTierResolver;

    private static final long REFRESH_TOKEN_EXPIRE_DAYS = 14;
    private static final String REFRESH_KEY_PREFIX = "auth:refresh:";
    private static final String BLACKLIST_KEY_PREFIX = "auth:blacklist:";
    private static final String PROVIDER_GITHUB = "github";

    private static final String KEY_ACCESS_TOKEN = "accessToken";
    private static final String KEY_REFRESH_TOKEN = "refreshToken";

    private Map<String, String> issueTokens(Users user) {

        String accessToken = jwtProvider.createAccessToken(user.getUserId(), user.getUserEmail());
        String refreshToken = jwtProvider.createRefreshToken(user.getUserId(), user.getUserEmail());

        // Redis 저장
        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + user.getUserId(),
                refreshToken,
                REFRESH_TOKEN_EXPIRE_DAYS,
                TimeUnit.DAYS);

        return Map.of(
                KEY_ACCESS_TOKEN, accessToken,
                KEY_REFRESH_TOKEN, refreshToken);
    }

    private String normalizeGithubEmail(GitHubUserResponse gitHubUser) {
        if (gitHubUser.getEmail() != null && !gitHubUser.getEmail().isBlank()) {
            return gitHubUser.getEmail();
        }
        return "github-" + gitHubUser.getId() + "@noemail.com";
    }

    // ---------------------------------------------------------
    // 회원가입
    // ---------------------------------------------------------
    @Override
    public Long register(UserRegisterRequestDto dto, MultipartFile imageFile) {

        if (!emailVerificationService.isVerified(dto.getUserEmail())) {
            throw new CustomBusinessException(UserErrorCode.EMAIL_NOT_VERIFIED);
        }

        if (userMapper.findByEmail(dto.getUserEmail()) != null) {
            throw new CustomBusinessException(UserErrorCode.EMAIL_DUPLICATE);
        }

        if (userMapper.findByNickname(dto.getUserNickname()) != null) {
            throw new CustomBusinessException(UserErrorCode.NICKNAME_DUPLICATE);
        }

        Users users = new Users();
        users.setUserEmail(dto.getUserEmail());
        users.setUserPw(passwordEncoder.encode(dto.getUserPw()));
        users.setUserName(dto.getUserName());
        users.setUserNickname(dto.getUserNickname());
        users.setUserImage(null);
        users.setUserEnabled(true);

        int result = userMapper.insertUser(users);
        if (result <= 0) {
            throw new CustomBusinessException(UserErrorCode.USER_CREATE_FAIL);
        }

        Long userId = users.getUserId();

        // 프로필 이미지 업로드
        String imageUrl;
        if (imageFile != null && !imageFile.isEmpty()) {
            String folderPath = "profile-images/" + dto.getUserNickname() + "/profile";
            try {
                imageUrl = s3Uploader.upload(imageFile, folderPath);
            } catch (IOException e) {
                throw new CustomBusinessException(UserErrorCode.FILE_SAVE_ERROR);
            }
        } else {
            imageUrl = "https://codenemsy.s3.ap-northeast-2.amazonaws.com/profile-images/default.png";
        }

        int updated = userMapper.updateUserImage(userId, imageUrl);
        if (updated != 1) {
            throw new CustomBusinessException(UserErrorCode.USER_UPDATE_FAILED);
        }
        return userId;
    }

    // ---------------------------------------------------------
    // 로그인 (DB + Redis 혼합 방식 적용)
    // ---------------------------------------------------------
    @Override
    public UserLoginResponseDto login(UserLoginRequestDto dto) {

        Users users = userMapper.findByEmail(dto.getUserEmail());
        if (users == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        if (!passwordEncoder.matches(dto.getUserPw(), users.getUserPw())) {
            throw new CustomBusinessException(UserErrorCode.INVALID_PASSWORD);
        }

        Map<String, String> tokens = issueTokens(users);

        SubscriptionTier tier = subscriptionTierResolver.resolveTier(String.valueOf(users.getUserId()));

        UserResponseDto userDto = UserResponseDto.builder()
                .userId(users.getUserId())
                .userEmail(users.getUserEmail())
                .userName(users.getUserName())
                .userNickname(users.getUserNickname())
                .userImage(users.getUserImage())
                .userGrade(users.getUserGrade())
                .userRole(users.getUserRole())
                .userEnabled(users.getUserEnabled())
                .githubId(users.getGithubId())
                .hasGithubToken(users.getGithubToken() != null && !users.getGithubToken().isBlank())
                .subscriptionTier(tier.name())
                .build();

        return UserLoginResponseDto.builder()
                .accessToken(tokens.get(KEY_ACCESS_TOKEN))
                .refreshToken(tokens.get(KEY_REFRESH_TOKEN))
                .user(userDto)
                .build();
    }

    // ---------------------------------------------------------
    // Access Token 재발급 (Redis + DB 검증)
    // ---------------------------------------------------------
    @Override
    public String refresh(String bearerToken) {

        String refreshToken = stripBearer(bearerToken);

        if (!jwtProvider.validateToken(refreshToken)) {
            throw new CustomBusinessException(UserErrorCode.INVALID_TOKEN);
        }

        Long userId = jwtProvider.getUserId(refreshToken);

        // 1) Redis 검증
        String redisToken = redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + userId);
        if (redisToken == null || !redisToken.equals(refreshToken)) {
            throw new CustomBusinessException(UserErrorCode.INVALID_TOKEN);
        }

        return jwtProvider.createAccessToken(userId, jwtProvider.getEmail(refreshToken));
    }

    // ---------------------------------------------------------
    // 로그아웃 (Redis + DB 삭제)
    // ---------------------------------------------------------
    @Override
    public boolean logout(String bearerToken) {

        try {
            String token = stripBearer(bearerToken);

            if (!jwtProvider.validateToken(token))
                return false;

            Long userId = jwtProvider.getUserId(token);

            // 1) Redis refresh 삭제
            redisTemplate.delete(REFRESH_KEY_PREFIX + userId);

            // AccessToken 블랙리스트 처리
            long expireAt = jwtProvider.getTokenRemainingTime(token);

            if (expireAt > 0) {
                redisTemplate.opsForValue().set(
                        BLACKLIST_KEY_PREFIX + token,
                        "logout",
                        expireAt,
                        TimeUnit.MILLISECONDS);
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

        Users users = userMapper.findByEmail(email);
        if (users == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        String token = passwordResetTokenService.createResetToken(users.getUserId());
        String resetUrl = frontendBaseUrl + "/reset-password?token=" + token;

        boolean sent = emailVerificationService.send(
                email,
                "[서비스명] 비밀번호 재설정 안내",
                "아래 링크를 클릭하여 비밀번호를 재설정하세요.\n\n" +
                        resetUrl + "\n\n" +
                        "본 링크는 15분 동안 유효합니다.");

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

        Long userId = passwordResetTokenService.validateToken(token);
        if (userId == null)
            return false;

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
    // 사용자 정보 수정
    // ---------------------------------------------------------
    @Override
    public UserResponseDto updateUserInfo(Long userId, UserUpdateRequestDto dto, MultipartFile image) {

        Users user = userMapper.findById(userId);
        if (user == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        // Process updates with validation
        String newName = processField(dto.getUserName(), user.getUserName());
        String newNickname = processField(dto.getUserNickname(), user.getUserNickname());

        validateNickname(userId, newNickname);

        String newImage = processImage(image, user.getUserImage());

        updateUserFields(user, dto, newName, newNickname, newImage);
        int updated = userMapper.updateUser(user);
        if (updated != 1) {
            throw new CustomBusinessException(UserErrorCode.USER_UPDATE_FAILED);
        }

        return buildUserResponse(user);
    }

    private String processField(String newValue, String currentValue) {
        if (newValue == null) {
            return currentValue;
        }
        return newValue.trim().isEmpty() ? null : newValue.trim();
    }

    private void validateNickname(Long userId, String nickname) {
        if (nickname == null) {
            return;
        }

        Users existing = userMapper.findByNickname(nickname);
        if (existing != null && !existing.getUserId().equals(userId)) {
            throw new CustomBusinessException(UserErrorCode.NICKNAME_DUPLICATE);
        }
    }

    private String processImage(MultipartFile image, String currentImage) {
        if (image == null || image.isEmpty()) {
            return currentImage;
        }

        try {
            return s3Uploader.upload(image, "profile");
        } catch (IOException e) {
            throw new CustomBusinessException(UserErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    private void updateUserFields(Users user, UserUpdateRequestDto dto,
                                  String newName, String newNickname, String newImage) {
        user.setUserName(newName);
        user.setUserNickname(newNickname);
        user.setUserImage(newImage);

        // GitHub 정보 처리
        if (dto.getGithubId() != null) {
            user.setGithubId(dto.getGithubId());
        }
        if (dto.getGithubToken() != null && !dto.getGithubToken().isBlank()) {
            user.setGithubToken(encryptionUtil.encrypt(dto.getGithubToken()));
        }
    }

    private UserResponseDto buildUserResponse(Users updated) {
        SubscriptionTier tier = subscriptionTierResolver.resolveTier(String.valueOf(updated.getUserId()));

        return UserResponseDto.builder()
                .userId(updated.getUserId())
                .userEmail(updated.getUserEmail())
                .userName(updated.getUserName())
                .userNickname(updated.getUserNickname())
                .userImage(updated.getUserImage())
                .userGrade(updated.getUserGrade())
                .userRole(updated.getUserRole())
                .userEnabled(updated.getUserEnabled())
                .githubId(updated.getGithubId())
                .hasGithubToken(updated.getGithubToken() != null && !updated.getGithubToken().isBlank())
                .subscriptionTier(tier.name())
                .build();
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
    public UserResponseDto getUserInfo(Long userId) {

        Users users = userMapper.findById(userId);

        if (users == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }
        return buildUserResponse(users);
    }

    // ============================================================
    // 90일 뒤 탈퇴 예약
    // ============================================================
    @Override
    public boolean requestDelete(Long userId) {

        Users users = userMapper.findById(userId);
        if (users == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        if (Boolean.TRUE.equals(users.getUserIsdeleted())) {
            throw new CustomBusinessException(UserErrorCode.ALREADY_SCHEDULED_DELETE);
        }

        LocalDateTime deletedAt = LocalDateTime.now().plusDays(90);
        int result = userMapper.scheduleDelete(userId, deletedAt);

        return result > 0;
    }

    // ============================================================
    // 탈퇴 신청 복구
    // ============================================================
    @Override
    public boolean restoreUser(Long userId) {

        Users users = userMapper.findById(userId);
        if (users == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        if (!Boolean.TRUE.equals(users.getUserIsdeleted())) {
            return false;
        }

        if (users.getUserDeletedat() != null && users.getUserDeletedat().isBefore(LocalDateTime.now())) {
            return false;
        }

        int result = userMapper.restoreUser(userId);
        return result > 0;
    }

    @Override
    public GithubLoginResult githubLogin(GitHubUserResponse gitHubUser, boolean linkMode) {

        if (linkMode) {
            throw new IllegalStateException("githubLogin must not be called in link mode");
        }

        String providerId = String.valueOf(gitHubUser.getId());
        String email = normalizeGithubEmail(gitHubUser);

        Users linkedUser = userMapper.findBySocialProvider(PROVIDER_GITHUB, providerId);
        if (linkedUser != null) {
            return buildLoginResult(linkedUser, false, null);
        }

        Users existingUser = userMapper.findByEmail(email);
        if (existingUser != null) {
            return buildLoginResult(existingUser, true, gitHubUser);
        }

        Users newUser = createNewGithubUser(gitHubUser);
        return buildLoginResult(newUser, false, null);
    }

    private GithubLoginResult buildLinkModeResult() {
        return GithubLoginResult.builder()
                .user(null)
                .needLink(false)
                .accessToken(null)
                .refreshToken(null)
                .build();
    }

    private GithubLoginResult buildLoginResult(Users user, boolean needLink, GitHubUserResponse gitHubUser) {
        Map<String, String> tokens = issueTokens(user);

        return GithubLoginResult.builder()
                .user(user)
                .needLink(needLink)
                .accessToken(tokens.get(KEY_ACCESS_TOKEN))
                .refreshToken(tokens.get(KEY_REFRESH_TOKEN))
                .gitHubUser(gitHubUser)
                .build();
    }

    // ---------------------------------------------------------
    // GitHub 연동 해제
    // ---------------------------------------------------------
    @Override
    public boolean disconnectGithub(Long userId) {

        Users user = userMapper.findById(userId);
        if (user == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        // GitHub provider 정보 삭제
        int result = userMapper.deleteSocialAccount(userId, PROVIDER_GITHUB);

        return result > 0;
    }

    // ---------------------------------------------------------
    // GitHub 연동 여부 확인
    // ---------------------------------------------------------
    @Override
    public boolean isGithubLinked(Long userId) {
        Integer count = userMapper.countSocialAccount(userId, PROVIDER_GITHUB);
        return count != null && count > 0;
    }

    // ---------------------------------------------------------
    // GitHub 연동 정보 조회
    // ---------------------------------------------------------
    @Override
    public Map<String, Object> getGithubUserInfo(Long userId) {
        return userMapper.getGithubUserInfo(userId);
    }

    // ---------------------------------------------------------
    // GitHub 신규 계정 생성 (provider 충돌 시 사용)
    // ---------------------------------------------------------
    private Users createNewGithubUser(GitHubUserResponse gitHubUser) {

        String providerId = String.valueOf(gitHubUser.getId());

        // 1) 이메일 정규화
        String normalizedEmail = normalizeGithubEmail(gitHubUser);

        // 2) 같은 이메일 계정 존재 시 반환
        Users existingByEmail = userMapper.findByEmail(normalizedEmail);
        if (existingByEmail != null) {
            return existingByEmail;
        }

        String randomPassword = UUID.randomUUID().toString();

        // 🔥 핵심: USER_NAME null 방어
        String userName = gitHubUser.getName();
        if (userName == null || userName.isBlank()) {
            userName = gitHubUser.getLogin(); // fallback
        }

        // (선택) nickname도 중복 방지
        String nickname = gitHubUser.getLogin();

        Users newUser = new Users();
        newUser.setUserEmail(normalizedEmail);
        newUser.setUserName(userName);
        newUser.setUserNickname(nickname);
        newUser.setUserImage(gitHubUser.getAvatarUrl());
        newUser.setUserPw(passwordEncoder.encode(randomPassword));
        newUser.setUserRole("ROLE_USER");
        newUser.setUserEnabled(true);

        int inserted = userMapper.insertUser(newUser);
        if (inserted != 1) {
            throw new CustomBusinessException(UserErrorCode.USER_CREATE_FAIL);
        }

        int socialInserted = userMapper.insertSocialAccount(
                newUser.getUserId(),
                PROVIDER_GITHUB,
                providerId,
                normalizedEmail
        );
        if (socialInserted != 1) {
            throw new CustomBusinessException(UserErrorCode.USER_UPDATE_FAILED);
        }

        return newUser;
    }

    @Override
    public boolean linkGithubAccount(Long currentUserId, GitHubUserResponse gitHubUser) {

        log.info("[GitHub 연동] gitHubUser 전체: {}", gitHubUser);

        String providerId = String.valueOf(gitHubUser.getId());

        // 1) 이미 다른 사용자와 연결된 경우
        Users existingLinkedUser = userMapper.findBySocialProvider(PROVIDER_GITHUB, providerId);
        if (existingLinkedUser != null && !existingLinkedUser.getUserId().equals(currentUserId)) {
            throw new CustomBusinessException(UserErrorCode.SOCIAL_ALREADY_LINKED);
        }

        // 2) 이미 본인 계정에 연결된 경우 → true 반환
        if (existingLinkedUser != null) {
            return true;
        }

        // 3) 이메일 체크 + 임시 이메일 생성
        String email = normalizeGithubEmail(gitHubUser);

        // 4) 신규 연동 저장
        int socialInserted = userMapper.insertSocialAccount(
                currentUserId,
                PROVIDER_GITHUB,
                providerId,
                email
        );

        if (socialInserted != 1) {
            throw new CustomBusinessException(UserErrorCode.USER_UPDATE_FAILED);
        }

        return true;
    }

    private String stripBearer(String bearerToken) {
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return bearerToken;
    }
}
