package kr.or.kosa.backend.batch.step;


import kr.or.kosa.backend.batch.tasklet.report.MonthlyMauStatsTasklet;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class MonthlyMauStatsStepConfig {

    @Bean
    public Step monthlyMauStatsStep(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        MonthlyMauStatsTasklet tasklet
    ) {
        return new StepBuilder("monthlyMauStatsStep", jobRepository)
            .tasklet(tasklet, transactionManager)
            .build();
    }
}
