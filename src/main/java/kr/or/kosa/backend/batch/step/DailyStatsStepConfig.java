package kr.or.kosa.backend.batch.step;

import kr.or.kosa.backend.batch.mapper.AdminStatisticsBatchMapper;
import kr.or.kosa.backend.batch.tasklet.report.DailyStatsTasklet;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class DailyStatsStepConfig {
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final AdminStatisticsBatchMapper adminStatisticsBatchMapper;

    public DailyStatsStepConfig(JobRepository jobRepository, PlatformTransactionManager transactionManager, AdminStatisticsBatchMapper adminStatisticsBatchMapper) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.adminStatisticsBatchMapper = adminStatisticsBatchMapper;
    }

    @Bean
    public Step dailyStatsStep(){
        return new StepBuilder("dailyStatsStep", jobRepository)
            .tasklet(
                new DailyStatsTasklet(adminStatisticsBatchMapper)
                    ,transactionManager
            )
            .build();
    }
}
