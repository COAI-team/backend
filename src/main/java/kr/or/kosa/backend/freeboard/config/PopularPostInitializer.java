package kr.or.kosa.backend.freeboard.config;

import kr.or.kosa.backend.freeboard.service.PopularFreeboardService;
import kr.or.kosa.backend.codeboard.service.PopularCodeboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PopularPostInitializer implements ApplicationRunner {

    private final PopularFreeboardService popularFreeboardService;
    private final PopularCodeboardService popularCodeboardService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("=== 인기글 초기화 시작 ===");

        try {
            // 자유게시판 인기글이 비어있으면 배치 실행
            if (popularFreeboardService.isPopularPostsEmpty()) {
                log.info("자유게시판 인기글이 비어있음 - 배치 실행");
                popularFreeboardService.processWeeklyPopularPosts();
            } else {
                log.info("자유게시판 인기글이 이미 존재함 - 스킵");
            }

            // 코드게시판 인기글이 비어있으면 배치 실행
            if (popularCodeboardService.isPopularPostsEmpty()) {
                log.info("코드게시판 인기글이 비어있음 - 배치 실행");
                popularCodeboardService.processWeeklyPopularPosts();
            } else {
                log.info("코드게시판 인기글이 이미 존재함 - 스킵");
            }

            log.info("=== 인기글 초기화 완료 ===");
        } catch (Exception e) {
            log.error("인기글 초기화 실패", e);
            // 실패해도 애플리케이션은 계속 실행되도록 예외를 먹음
        }
    }
}