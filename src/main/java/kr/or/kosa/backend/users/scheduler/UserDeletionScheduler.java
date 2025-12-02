package kr.or.kosa.backend.users.scheduler;

import kr.or.kosa.backend.users.domain.Users;
import kr.or.kosa.backend.users.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserDeletionScheduler {

    private final UserMapper userMapper;

    // 매일 새벽 3시 실행
    @Scheduled(cron = "0 0 3 * * *")
    public void deleteAfter90Days() {

        LocalDateTime now = LocalDateTime.now();

        List<Users> users = userMapper.findUsersToDelete(now);

        for (Users u : users) {

            Long userId = u.getUserId(); // 필드명 변경

            // Soft delete 적용
            if (userMapper.softDeleteUser(userId) <= 0) {
                log.warn("Soft Delete failed for userId={}", userId);
            }

            // 개인정보 익명화
            if (userMapper.anonymizeUser(
                    userId,
                    "deleted_" + userId + "@deleted.com",
                    "탈퇴회원"
            ) <= 0) {
                log.warn("Anonymize failed for userId={}", userId);
            }
        }
    }
}