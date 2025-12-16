package kr.or.kosa.backend.batch.job.report;

import kr.or.kosa.backend.batch.tasklet.report.DailyBoardStatisticsTasklet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 일간 게시판 통계 배치 Job 설정
 *
 * 매일 자정에 실행되어 전날(D-1) 게시판 통계를 집계합니다.
 * - Job 이름: dailyBoardStatisticsJob
 * - Step 이름: dailyBoardStatisticsStep
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DailyBoardStatisticsJobConfig {

    private final DailyBoardStatisticsTasklet dailyBoardStatisticsTasklet;

    /**
     * 일간 게시판 통계 Job
     *
     * @param jobRepository Job 메타데이터 저장소
     * @param dailyBoardStatisticsStep 통계 집계 Step
     * @return Job
     */
    @Bean
    public Job dailyBoardStatisticsJob(
            JobRepository jobRepository,
            Step dailyBoardStatisticsStep) {
        return new JobBuilder("dailyBoardStatisticsJob", jobRepository)
                .start(dailyBoardStatisticsStep)
                .build();
    }

    /**
     * 일간 게시판 통계 Step
     *
     * Tasklet 방식으로 통계를 집계합니다.
     *
     * @param jobRepository Job 메타데이터 저장소
     * @param transactionManager 트랜잭션 매니저
     * @return Step
     */
    @Bean
    public Step dailyBoardStatisticsStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager) {
        return new StepBuilder("dailyBoardStatisticsStep", jobRepository)
                .tasklet(dailyBoardStatisticsTasklet, transactionManager)
                .build();
    }
}