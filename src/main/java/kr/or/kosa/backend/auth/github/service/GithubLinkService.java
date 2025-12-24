package kr.or.kosa.backend.auth.github.service;

import kr.or.kosa.backend.auth.github.dto.GitHubUserResponse;
import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import kr.or.kosa.backend.users.domain.Users;
import kr.or.kosa.backend.users.exception.UserErrorCode;
import kr.or.kosa.backend.users.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GithubLinkService {

    private static final String PROVIDER_GITHUB = "GITHUB";

    private final UserMapper userMapper;

    /**
     * 🔒 GitHub 계정 연동 (트랜잭션 보장)
     */
    @Transactional
    public boolean linkGithubInternal(Long currentUserId, GitHubUserResponse gitHubUser) {

        log.info("[GitHub 연동] 요청 userId={}, githubId={}",
                currentUserId, gitHubUser.getId());

        String providerId = String.valueOf(gitHubUser.getId());

        // 1) 이미 다른 사용자에게 연결된 GitHub 계정인지 확인
        Users existingLinkedUser =
                userMapper.findBySocialProvider(PROVIDER_GITHUB, providerId);

        if (existingLinkedUser != null &&
                !existingLinkedUser.getUserId().equals(currentUserId)) {
            throw new CustomBusinessException(UserErrorCode.SOCIAL_ALREADY_LINKED);
        }

        // 2) 이미 본인 계정에 연동된 경우 (멱등)
        if (existingLinkedUser != null) {
            log.info("[GitHub 연동] 이미 연동된 상태 userId={}", currentUserId);
            return true;
        }

        // 3) 이메일 정규화
        String email = normalizeGithubEmail(gitHubUser);

        // 4) social_login 테이블 INSERT
        int inserted = userMapper.insertSocialAccount(
                currentUserId,
                PROVIDER_GITHUB,
                providerId,
                email
        );

        if (inserted != 1) {
            throw new CustomBusinessException(UserErrorCode.USER_UPDATE_FAILED);
        }

        log.info("[GitHub 연동 완료] userId={}, githubId={}",
                currentUserId, providerId);

        return true;
    }

    /**
     * 이메일 정규화
     */
    private String normalizeGithubEmail(GitHubUserResponse gitHubUser) {
        if (gitHubUser.getEmail() == null || gitHubUser.getEmail().isBlank()) {
            return gitHubUser.getLogin() + "@github.com";
        }
        return gitHubUser.getEmail().toLowerCase();
    }
}
