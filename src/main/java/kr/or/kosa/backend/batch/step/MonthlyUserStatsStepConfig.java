package kr.or.kosa.backend.batch.step;

import kr.or.kosa.backend.batch.tasklet.report.MonthlyUserStatsTasklet;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class MonthlyUserStatsStepConfig {

    @Bean
    public Step monthlyUserStatsStep(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        MonthlyUserStatsTasklet monthlyUserStatsTasklet
    ){
        return new StepBuilder("monthlyUserStatsStep", jobRepository)
            .tasklet(monthlyUserStatsTasklet, transactionManager)
            .build();
    }
}
