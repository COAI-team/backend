package kr.or.kosa.backend.user.service;

import kr.or.kosa.backend.user.domain.User;
import kr.or.kosa.backend.user.dto.*;
import kr.or.kosa.backend.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import kr.or.kosa.backend.exception.CustomException;
import kr.or.kosa.backend.exception.ErrorCode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Override
    public int register(UserRegisterRequestDto dto, MultipartFile imageFile) {

        if (!uploadDir.endsWith("/")) {
            uploadDir = uploadDir + "/";
        }

        // 🔥 1) 이메일 인증 여부 확인 (여기가 핵심)
        if (!emailVerificationService.isVerified(dto.getEmail())) {
            throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        // 🔥 2) 이메일 중복 체크
        if (userMapper.findByEmail(dto.getEmail()) != null) {
            throw new CustomException(ErrorCode.EMAIL_DUPLICATE);
        }

        // 🔥 3) 닉네임 중복 체크
        if (userMapper.findByNickname(dto.getNickname()) != null) {
            throw new CustomException(ErrorCode.NICKNAME_DUPLICATE);
        }

        // 4) 유저 생성
        User user = new User();
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setName(dto.getName());
        user.setNickname(dto.getNickname());
        user.setImage(null);
        user.setEnabled(true);

        userMapper.insertUser(user);
        int userId = user.getId();

        // 5) 이미지 처리
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
    public UserResponseDto login(UserLoginRequestDto dto) {
        User user = userMapper.findByEmail(dto.getEmail());

        if (user == null) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }

        return toResponseDto(user);
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
}