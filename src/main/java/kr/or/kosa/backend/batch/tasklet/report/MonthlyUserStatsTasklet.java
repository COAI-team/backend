package kr.or.kosa.backend.batch.tasklet.report;

import kr.or.kosa.backend.batch.mapper.AdminStatisticsBatchMapper;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class MonthlyUserStatsTasklet implements Tasklet {
    private final AdminStatisticsBatchMapper mapper;

    public MonthlyUserStatsTasklet(AdminStatisticsBatchMapper mapper) {
        this.mapper = mapper;
    }
    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        LocalDate localDate = LocalDate.now().minusMonths(1);
        int year = localDate.getYear();
        int month = localDate.getMonthValue();

        if(mapper.existsUserMonthlyStats(year, month) > 0 ){
            mapper.deleteUserMonthlyStats(year, month);
        }

        mapper.insertUserMonthlyStats(year, month);

        return RepeatStatus.FINISHED;
    }
}
