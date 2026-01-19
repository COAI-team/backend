package kr.or.kosa.backend.admin.mapper;

import kr.or.kosa.backend.admin.dto.*;
import kr.or.kosa.backend.admin.dto.LanguageRankingDto;
import kr.or.kosa.backend.admin.dto.UserCountSummaryDto;
import kr.or.kosa.backend.admin.dto.dashBoard.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AdminDashBoardMapper {
    List<UserCountSummaryDto> userSignUpCount();
    int todaySignUpCount();
    TodayPaymentSummaryDto todayPaymentSummary();
    List<PaymentSummaryDto> paymentSummary();
    List<LanguageRankingDto>  languageRanking();
    List<AlgoSolverRankingDto> algoSolverRanking();
    List<CodeBoardStateTotalDto> codeBoardStateTotal();
    List<CodeAnalysisRankingDto> codeAnalysisRanking();
    List<SummarySection> selectDailyStatsByRange(
        @Param("startDate") String startDate,
        @Param("endDate") String endDate
        );
    List<UserStatsDto> selectRecentUserMonthlyStats(@Param("limit") int limit);
    List<SalesStatsDto> selectMonthlySalesStats(@Param("limit") int limit);


    SummarySection selectLatestDailyStats();
    List<UserStatsDto> selectAllUserStats();
    List<SalesStatsDto> selectAllSalesStats();
    List<LanguageRankDto> selectLanguageRankingTop5();
    List<AlgoSolveRankingDto> selectAlgoSolveRankingTop5();
    List<CodeAnalysisRankDto> selectCodeAnalysisRankTop5();
    List<AnalysisTypeMonthlyStatsDto> selectAllAnalysisTypeMonthlyStats();
    List<MauMonthlyStatsDto> selectAllMauMonthlyStats();

}
