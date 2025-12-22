package kr.or.kosa.backend.admin.service;

import kr.or.kosa.backend.admin.dto.dashBoard.SalesStatsDto;
import kr.or.kosa.backend.admin.dto.dashBoard.SummarySection;
import kr.or.kosa.backend.admin.dto.dashBoard.UserStatsDto;
import kr.or.kosa.backend.admin.dto.response.AdminDashboardResponse;

import java.time.LocalDate;
import java.util.List;

public interface AdminBatchDashBoardService {
    AdminDashboardResponse dashBoards();

    List<SummarySection> getDailyStatsByRange(String startDate, String endDate);
    List<UserStatsDto> getUserMonthlyStats(int limit);
    List<SalesStatsDto> getSalesMonthlyStats(int limit);

}
