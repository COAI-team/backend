package kr.or.kosa.backend.admin.dto.response;

import kr.or.kosa.backend.admin.dto.*;

import java.util.List;

public record AdminDashBoardResponseDto(
    List<UserCountSummaryDto> userCountSummary,
    int todaySignUpCount,
    TodayPaymentSummaryDto todayPaymentSummary,
    List<PaymentSummaryDto> paymentSummary,
    List<LanguageRankingDto> languageRanking,
    List<AlgoSolverRankingDto> algoSolverRanking,
    List<CodeBoardStateTotalDto>  codeBoardStateTotal,
    List<CodeAnalysisRankingDto> codeAnalysisRanking
) {
}
