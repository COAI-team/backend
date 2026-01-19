package kr.or.kosa.backend.algorithm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 데일리 미션 스케줄러
 * 매일 자정에 모든 활성 사용자에게 데일리 미션 생성
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyMissionScheduler {

    private final DailyMissionService dailyMissionService;

    /**
     * 매일 자정에 실행
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void createDailyMissions() {
        log.info("데일리 미션 생성 스케줄러 시작");

        try {
            int createdCount = dailyMissionService.createDailyMissionsForAllUsers();
            log.info("데일리 미션 생성 스케줄러 완료 - {}명 처리", createdCount);
        } catch (Exception e) {
            log.error("데일리 미션 생성 스케줄러 실패", e);
        }
    }
}
