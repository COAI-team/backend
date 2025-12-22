package kr.or.kosa.backend.batch.tasklet.report;

import kr.or.kosa.backend.batch.mapper.AdminStatisticsBatchMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 일간 게시판 통계 집계 Tasklet
 *
 * 전날(D-1)의 게시판 통계를 집계하여 저장합니다.
 * - 자유게시판
 * - 코드게시판
 * - 알고리즘게시판
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyStatsTasklet implements Tasklet {

    private final AdminStatisticsBatchMapper adminStatisticsBatchMapper;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        LocalDate targetDate = LocalDate.now().minusDays(1);
        adminStatisticsBatchMapper.insertDailyStats(targetDate);
        return RepeatStatus.FINISHED;
    }


}