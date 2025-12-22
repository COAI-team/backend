package kr.or.kosa.backend.batch.tasklet.report;


import kr.or.kosa.backend.batch.mapper.AdminStatisticsBatchMapper;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class MonthlyCodeAnalysisRankingTasklet implements Tasklet {
    private final AdminStatisticsBatchMapper adminStatisticsBatchMapper;

    public MonthlyCodeAnalysisRankingTasklet(AdminStatisticsBatchMapper adminStatisticsBatchMapper) {
        this.adminStatisticsBatchMapper = adminStatisticsBatchMapper;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        LocalDate target = LocalDate.now();
        int  year = target.getYear();
        int month = target.getMonthValue();

        adminStatisticsBatchMapper.deleteCodeAnalysisRankingMonthly(year, month);
        adminStatisticsBatchMapper.insertMonthlyCodeAnalysisRanking(year, month);

        return RepeatStatus.FINISHED;
    }
}
