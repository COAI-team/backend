package kr.or.kosa.backend.admin.service;


import kr.or.kosa.backend.admin.dto.dashBoard.*;
import kr.or.kosa.backend.admin.dto.response.AdminDashboardResponse;
import kr.or.kosa.backend.admin.mapper.AdminDashBoardMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminBatchDashBoardServiceImpl implements AdminBatchDashBoardService {
    private final AdminDashBoardMapper adminDashBoardMapper;

    public AdminBatchDashBoardServiceImpl(AdminDashBoardMapper adminDashBoardMapper) {
        this.adminDashBoardMapper = adminDashBoardMapper;
    }

    @Override
    public AdminDashboardResponse dashBoards() {

//        SummarySection ss = adminDashBoardMapper.selectLatestDailyStats();
//        List<UserStatsDto> aa = adminDashBoardMapper.selectAllUserStats();
//        List<SalesStatsDto> aaa = adminDashBoardMapper.selectAllSalesStats();
//        List<LanguageRankDto> aaaaa = adminDashBoardMapper.selectLanguageRankingTop5();
//        List<AlgoSolveRankingDto> bbb = adminDashBoardMapper.selectAlgoSolveRankingTop5();
//        List<CodeAnalysisRankDto> bbbbbb = adminDashBoardMapper.selectCodeAnalysisRankTop5();
//        List<AnalysisTypeMonthlyStatsDto> asd =  adminDashBoardMapper.selectAllAnalysisTypeMonthlyStats();
//        List<MauMonthlyStatsDto> asgv = adminDashBoardMapper.selectAllMauMonthlyStats();

//        System.out.println("=== Admin Dashboard Debug Start ===");
//
//        System.out.println("SummarySection ss = " + ss);
//
//        System.out.println("UserStatsDto list aa = " + aa);
//        System.out.println("UserStatsDto count = " + (aa != null ? aa.size() : "null"));
//
//        System.out.println("SalesStatsDto list aaa = " + aaa);
//        System.out.println("SalesStatsDto count = " + (aaa != null ? aaa.size() : "null"));
//
//        System.out.println("LanguageRankDto list aaaaa = " + aaaaa);
//        System.out.println("LanguageRankDto count = " + (aaaaa != null ? aaaaa.size() : "null"));
//
//        System.out.println("AlgoSolveRankingDto list bbb = " + bbb);
//        System.out.println("AlgoSolveRankingDto count = " + (bbb != null ? bbb.size() : "null"));
//
//        System.out.println("CodeAnalysisRankDto list bbbbbb = " + bbbbbb);
//        System.out.println("CodeAnalysisRankDto count = " + (bbbbbb != null ? bbbbbb.size() : "null"));
//
//        System.out.println("AnalysisTypeMonthlyStatsDto list asd = " + asd);
//        System.out.println("AnalysisTypeMonthlyStatsDto count = " + (asd != null ? asd.size() : "null"));
//
//        System.out.println("MauMonthlyStatsDto list asgv = " + asgv);
//        System.out.println("MauMonthlyStatsDto count = " + (asgv != null ? asgv.size() : "null"));
//
//        System.out.println("=== Admin Dashboard Debug End ===");

        return new AdminDashboardResponse(
            adminDashBoardMapper.selectLatestDailyStats(),
            adminDashBoardMapper.selectAllUserStats(),
            adminDashBoardMapper.selectAllSalesStats(),
            adminDashBoardMapper.selectLanguageRankingTop5(),
            adminDashBoardMapper.selectAlgoSolveRankingTop5(),
            adminDashBoardMapper.selectCodeAnalysisRankTop5(),
            adminDashBoardMapper.selectAllAnalysisTypeMonthlyStats(),
            adminDashBoardMapper.selectAllMauMonthlyStats()
        );
    }

    @Override
    public List<SummarySection> getDailyStatsByRange(String startDate, String endDate) {
        return adminDashBoardMapper.selectDailyStatsByRange(startDate, endDate);
    }

    @Override
    public List<UserStatsDto> getUserMonthlyStats(int limit) {
        return adminDashBoardMapper.selectRecentUserMonthlyStats(limit);
    }

    @Override
    public List<SalesStatsDto> getSalesMonthlyStats(int limit) {
        return adminDashBoardMapper.selectMonthlySalesStats(limit);
    }


}
