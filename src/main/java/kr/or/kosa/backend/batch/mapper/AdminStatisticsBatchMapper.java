package kr.or.kosa.backend.batch.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 게시판 통계 배치용 Mapper
 *
 * 일간 게시판 통계 데이터를 집계하고 저장하는 MyBatis Mapper
 */
@Mapper
public interface AdminStatisticsBatchMapper {
    void insertDailyStats(@Param("targetDate")LocalDate targetDate);

    // 오늘 생성된 알고리즘 게시판
    int todayAlgoBoardCount();
    // 오늘 생성된 코드분석 게시판
    int todayCodeBoardCount();
    // 오늘 생성된 자유게시판
    int todayFreeBoardCount();


    // 결제 관련
    int exists(@Param("year") int year,
               @Param("month") int month);

    int delete(@Param("year") int year,
               @Param("month") int month);

    void insertMonthlySalesStats(@Param("year") int year,
                                 @Param("month") int month);


    int deleteByPlan(@Param("year") int year,
               @Param("month") int month);

    void insertMonthlySalesByPlan(@Param("year") int year,
                                  @Param("month") int month);

    // 유저 관련
    int existsUserMonthlyStats(@Param("year") int year,
                               @Param("month") int month);

    int deleteUserMonthlyStats(@Param("year") int year,
                               @Param("month") int month);

    void insertUserMonthlyStats(@Param("year") int year,
                                @Param("month") int month);

    // 유저 MAU
    int deleteMonthlyMau(@Param("year") int year,
                         @Param("month") int month);

    int insertMonthlyMau(@Param("year") int year,
                         @Param("month") int month);

    // 월별 분선타입
    int deleteAnalysisTypeMonthly(
        @Param("year") int year,
        @Param("month") int month
    );

    int insertAnalysisTypeMonthly(
        @Param("year") int year,
        @Param("month") int month
    );


    // 월별 코드 분석 랭킹
    int deleteCodeAnalysisRankingMonthly(
        @Param("year") int year,
        @Param("month") int month
    );

    int insertMonthlyCodeAnalysisRanking(
        @Param("year") int year,
        @Param("month") int month
    );

    // 월별 알고리음 문제 푼
    int deleteAlgoSolveRankingMonthly(
        @Param("year") int year,
        @Param("month") int month
    );

    int insertMonthlyAlgoSolveRanking(
        @Param("year") int year,
        @Param("month") int month
    );

    // 언어별 랭킹
    int deleteLanguageRankingMonthly(
        @Param("year") int year,
        @Param("month") int month
    );

    int insertMonthlyLanguageRanking(
        @Param("year") int year,
        @Param("month") int month
    );



}