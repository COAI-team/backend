package kr.or.kosa.backend.admin.mapper;

import kr.or.kosa.backend.admin.dto.*;
import kr.or.kosa.backend.admin.dto.UserCountSummaryDto;
import org.apache.ibatis.annotations.Mapper;

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
}
