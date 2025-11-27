package kr.or.kosa.backend.user.scheduler;

import kr.or.kosa.backend.user.domain.User;
import kr.or.kosa.backend.user.mapper.UserMapper;
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

        List<User> users = userMapper.findUsersToDelete(now);

        for (User u : users) {

            // Soft delete 적용
            if (userMapper.softDeleteUser(u.getId()) <= 0) {
                log.warn("Soft Delete failed for userId={}", u.getId());
            }

            // 개인정보 익명화
            if (userMapper.anonymizeUser(
                    u.getId(),
                    "deleted_" + u.getId() + "@deleted.com",
                    "탈퇴회원"
            ) <= 0) {
                log.warn("Anonymize failed for userId={}", u.getId());
            }
        }
    }
}