package kr.or.kosa.backend.batch.tasklet.report;

import kr.or.kosa.backend.batch.mapper.AdminStatisticsBatchMapper;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;


@Component
public class MonthlySalesStatsTasklet implements Tasklet {
    private final AdminStatisticsBatchMapper adminStatisticsBatchMapper;

    public MonthlySalesStatsTasklet(AdminStatisticsBatchMapper adminStatisticsBatchMapper) {
        this.adminStatisticsBatchMapper = adminStatisticsBatchMapper;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {

        LocalDate target = LocalDate.now().minusMonths(1);
        int year = target.getYear();
        int month = target.getMonthValue();

        // 전체 매출
        adminStatisticsBatchMapper.delete(year, month);
        adminStatisticsBatchMapper.insertMonthlySalesStats(year, month);

        // 플랜별 매출
        adminStatisticsBatchMapper.deleteByPlan(year, month);
        adminStatisticsBatchMapper.insertMonthlySalesByPlan(year, month);

        return RepeatStatus.FINISHED;
    }
}
