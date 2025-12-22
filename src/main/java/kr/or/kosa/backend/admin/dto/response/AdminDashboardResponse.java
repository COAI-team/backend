package kr.or.kosa.backend.admin.dto.response;

import kr.or.kosa.backend.admin.dto.dashBoard.*;

import java.util.List;

public record AdminDashboardResponse(
    // 🔹 상단 요약 (전일 기준)
    SummarySection summary,

    // 🔹 일별 추이 (최근 N일 / 기간 조회용)
//    List<DailyStatsDto> dailyStats,

    // 🔹 월별 유저 통계
    List<UserStatsDto> userMonthlyStats,

    // 🔹 월별 매출 통계
    List<SalesStatsDto> salesMonthlyStats,

    // 🔹 월별 언어 랭킹 TOP5
    List<LanguageRankDto> languageRankingTop5,

    // 🔹 월별 알고리즘 풀이 랭킹 TOP5
    List<AlgoSolveRankingDto> algoSolveRankingTop5,

    // 🔹 월별 코드 분석 랭킹 TOP5
    List<CodeAnalysisRankDto> codeAnalysisRankingTop5,

    // 🔹 월별 분석 타입 분포
    List<AnalysisTypeMonthlyStatsDto> analysisTypeMonthlyStats,

    // 🔹 월별 MAU
    List<MauMonthlyStatsDto> mauMonthlyStats
) {
}
