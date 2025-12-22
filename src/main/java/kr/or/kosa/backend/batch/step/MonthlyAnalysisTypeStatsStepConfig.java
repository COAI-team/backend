package kr.or.kosa.backend.batch.step;

import kr.or.kosa.backend.batch.tasklet.report.MonthlyAnalysisTypeStatsTasklet;
import org.checkerframework.checker.units.qual.C;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class MonthlyAnalysisTypeStatsStepConfig {

    @Bean
    public Step monthlyAnalysisTypeStatsStep(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        MonthlyAnalysisTypeStatsTasklet tasklet
    ) {
        return new StepBuilder("monthlyAnalysisTypeStatsStep", jobRepository)
            .allowStartIfComplete(true)
            .tasklet(tasklet, transactionManager)
            .build();
    }
}
