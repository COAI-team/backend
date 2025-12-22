package kr.or.kosa.backend.batch.step;

import kr.or.kosa.backend.batch.tasklet.report.MonthlySalesStatsTasklet;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class MonthlySalesStatsStepConfig {

    @Bean
    public Step monthlySalesStatsStep(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        MonthlySalesStatsTasklet monthlySalesStatsTasklet
    ) {
        return new StepBuilder("monthlySalesStatsStep", jobRepository)
            .tasklet(monthlySalesStatsTasklet, transactionManager)
            .build();
    }
}
