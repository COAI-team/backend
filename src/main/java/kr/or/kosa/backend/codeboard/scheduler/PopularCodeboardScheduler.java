package kr.or.kosa.backend.codeboard.scheduler;

import kr.or.kosa.backend.codeboard.service.PopularCodeboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PopularCodeboardScheduler {

    private final PopularCodeboardService popularCodeboardService;

    // 매주 수요일 새벽 3시에 실행
    @Scheduled(cron = "0 0 3 * * WED")
    public void scheduleWeeklyPopularPosts() {
        log.info("코드게시판 주간 인기글 배치 스케줄러 시작");
        try {
            popularCodeboardService.processWeeklyPopularPosts();
        } catch (Exception e) {
            log.error("코드게시판 주간 인기글 배치 실패", e);
        }
    }
}