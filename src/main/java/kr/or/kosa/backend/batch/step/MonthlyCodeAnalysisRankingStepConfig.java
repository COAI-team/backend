package kr.or.kosa.backend.batch.step;

import kr.or.kosa.backend.batch.tasklet.report.MonthlyCodeAnalysisRankingTasklet;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class MonthlyCodeAnalysisRankingStepConfig {

    @Bean
    public Step monthlyCodeAnalysisRankingStep(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        MonthlyCodeAnalysisRankingTasklet tasklet
    ) {
        return new StepBuilder("monthlyCodeAnalysisRankingStep", jobRepository)
            .allowStartIfComplete(true)
            .tasklet(tasklet, transactionManager)
            .build();
    }
}
