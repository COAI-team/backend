package kr.or.kosa.backend.batch.job.report;


import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * 일간 게시판 통계 배치 Job 설정
 *
 * 매일 자정에 실행되어 전날(D-1) 게시판 통계를 집계합니다.
 * - Job 이름: dailyBoardStatisticsJob
 * - Step 이름: dailyBoardStatisticsStep
 */
@Slf4j
@Configuration
public class AdminStaticsJobConfig {

    @Bean
    public Job adminStaticsJob(
        JobRepository jobRepository,
        Step dailyStatsStep,
        Step monthlySalesStatsStep,
        Step monthlyUserStatsStep,
        Step monthlyMauStatsStep,
        Step monthlyAnalysisTypeStatsStep,
        Step monthlyCodeAnalysisRankingStep,
        Step monthlyAlgoSolveRankingStep,
        Step monthlyLanguageRankingStep
    ) {
        return new JobBuilder("adminStatisticsJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(dailyStatsStep)
            .next(monthlySalesStatsStep)
            .next(monthlyUserStatsStep)
            .next(monthlyMauStatsStep)
            .next(monthlyAnalysisTypeStatsStep)
            .next(monthlyCodeAnalysisRankingStep)
            .next(monthlyAlgoSolveRankingStep)
            .next(monthlyLanguageRankingStep)
            .build();
    }
}
