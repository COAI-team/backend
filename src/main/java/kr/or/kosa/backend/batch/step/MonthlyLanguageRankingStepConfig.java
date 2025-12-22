package kr.or.kosa.backend.batch.step;

import kr.or.kosa.backend.batch.tasklet.report.MonthlyLanguageRankingTasklet;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class MonthlyLanguageRankingStepConfig {

    @Bean
    public Step monthlyLanguageRankingStep(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        MonthlyLanguageRankingTasklet tasklet
    ) {
        return new StepBuilder("monthlyLanguageRankingStep", jobRepository)
            .tasklet(tasklet, transactionManager)
            .build();
    }
}
