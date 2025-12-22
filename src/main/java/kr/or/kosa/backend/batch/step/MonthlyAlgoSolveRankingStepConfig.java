package kr.or.kosa.backend.batch.step;

import kr.or.kosa.backend.batch.tasklet.report.MonthlyAlgoSolveRankingTasklet;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class MonthlyAlgoSolveRankingStepConfig {

    @Bean
    public Step monthlyAlgoSolveRankingStep(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        MonthlyAlgoSolveRankingTasklet tasklet
    ){
        return new StepBuilder("monthlyAlgoSolveRankingStep", jobRepository)
            .tasklet(tasklet, transactionManager)
            .allowStartIfComplete(true)
            .build();
    }
}
