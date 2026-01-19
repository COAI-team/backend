package kr.or.kosa.backend.tag.scheduler;

import kr.or.kosa.backend.tag.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TagScheduler {

    private final TagMapper tagMapper;

    // 매일 새벽 2시에 실행
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupUnusedTags() {
        log.info(">>> 사용되지 않는 태그 정리 시작");

        try {
            int deletedCount = tagMapper.deleteUnusedTags();
            log.info(">>> 사용되지 않는 태그 정리 완료: 삭제된 태그 수={}", deletedCount);
        } catch (Exception e) {
            log.error(">>> 태그 정리 중 오류 발생", e);
        }
    }
}