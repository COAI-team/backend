package kr.or.kosa.backend.batch.tasklet.report;

import kr.or.kosa.backend.batch.dto.DailyBoardStatisticsDto;
import kr.or.kosa.backend.batch.mapper.BoardStatisticsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
public class DailyBoardStatisticsTasklet implements Tasklet {

    private final BoardStatisticsMapper boardStatisticsMapper;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        log.info("===== 일간 게시판 통계 집계 시작 =====");

        // 집계 대상 날짜 (어제)
        LocalDate targetDate = LocalDate.now().minusDays(1);
        log.info("집계 대상 날짜: {}", targetDate);

        List<String> boardTypes = List.of("free", "code", "algo");
        List<DailyBoardStatisticsDto> statisticsList = new ArrayList<>();

        // 각 게시판별 통계 집계
        for (String boardType : boardTypes) {
            try {
                // 기존 통계가 있으면 삭제 (재집계)
                int existsCount = boardStatisticsMapper.checkStatisticsExists(targetDate, boardType);
                if (existsCount > 0) {
                    log.info("[{}] 기존 통계 데이터 삭제 중...", boardType);
                    boardStatisticsMapper.deleteDailyStatistics(targetDate, boardType);
                }

                // 게시판별 통계 집계
                DailyBoardStatisticsDto statistics = aggregateStatisticsByBoardType(boardType, targetDate);

                if (statistics != null && statistics.getPostCount() > 0) {
                    statisticsList.add(statistics);
                    log.info("[{}] 통계 집계 완료 - 게시글: {}건, 총 조회수: {}, 평균 조회수: {:.2f}",
                            boardType,
                            statistics.getPostCount(),
                            statistics.getTotalViews(),
                            statistics.getAvgViews());
                } else {
                    log.info("[{}] 해당 날짜에 게시글이 없습니다.", boardType);
                }

            } catch (Exception e) {
                log.error("[{}] 통계 집계 실패: {}", boardType, e.getMessage(), e);
                // 하나의 게시판 통계 실패해도 다른 게시판은 계속 진행
            }
        }

        // 통계 데이터 저장
        int savedCount = 0;
        for (DailyBoardStatisticsDto statistics : statisticsList) {
            try {
                boardStatisticsMapper.insertDailyStatistics(statistics);
                savedCount++;
                log.info("[{}] 통계 저장 완료", statistics.getBoardType());
            } catch (Exception e) {
                log.error("[{}] 통계 저장 실패: {}", statistics.getBoardType(), e.getMessage(), e);
            }
        }

        log.info("===== 일간 게시판 통계 집계 완료 (총 {}건 저장) =====", savedCount);

        // StepExecution에 저장된 통계 건수 기록
        contribution.incrementWriteCount(savedCount);

        return RepeatStatus.FINISHED;
    }

    /**
     * 게시판 타입별 통계 집계
     */
    private DailyBoardStatisticsDto aggregateStatisticsByBoardType(String boardType, LocalDate targetDate) {
        switch (boardType) {
            case "free":
                return boardStatisticsMapper.aggregateFreeboardStatistics(targetDate);
            case "code":
                return boardStatisticsMapper.aggregateCodeboardStatistics(targetDate);
            case "algo":
                return boardStatisticsMapper.aggregateAlgoboardStatistics(targetDate);
            default:
                log.warn("알 수 없는 게시판 타입: {}", boardType);
                return null;
        }
    }
}