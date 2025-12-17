package kr.or.kosa.backend.batch.mapper;

import kr.or.kosa.backend.batch.dto.DailyBoardStatisticsDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 게시판 통계 배치용 Mapper
 *
 * 일간 게시판 통계 데이터를 집계하고 저장하는 MyBatis Mapper
 */
@Mapper
public interface BoardStatisticsMapper {

    /**
     * 특정 날짜의 자유게시판 통계를 집계
     *
     * @param targetDate 집계 대상 날짜
     * @return 자유게시판 통계
     */
    DailyBoardStatisticsDto aggregateFreeboardStatistics(@Param("targetDate") LocalDate targetDate);

    /**
     * 특정 날짜의 코드게시판 통계를 집계
     *
     * @param targetDate 집계 대상 날짜
     * @return 코드게시판 통계
     */
    DailyBoardStatisticsDto aggregateCodeboardStatistics(@Param("targetDate") LocalDate targetDate);

    /**
     * 특정 날짜의 알고리즘게시판 통계를 집계
     *
     * @param targetDate 집계 대상 날짜
     * @return 알고리즘게시판 통계
     */
    DailyBoardStatisticsDto aggregateAlgoboardStatistics(@Param("targetDate") LocalDate targetDate);

    /**
     * 일간 통계 데이터를 저장
     *
     * @param statistics 저장할 통계 데이터
     * @return 저장된 행 수
     */
    int insertDailyStatistics(DailyBoardStatisticsDto statistics);

    /**
     * 특정 날짜의 통계가 이미 존재하는지 확인
     *
     * @param statDate 확인할 날짜
     * @param boardType 게시판 타입
     * @return 존재 여부 (존재하면 1, 없으면 0)
     */
    int checkStatisticsExists(@Param("statDate") LocalDate statDate, @Param("boardType") String boardType);

    /**
     * 기존 통계 데이터 삭제 (재집계 시 사용)
     *
     * @param statDate 삭제할 날짜
     * @param boardType 게시판 타입
     * @return 삭제된 행 수
     */
    int deleteDailyStatistics(@Param("statDate") LocalDate statDate, @Param("boardType") String boardType);
}