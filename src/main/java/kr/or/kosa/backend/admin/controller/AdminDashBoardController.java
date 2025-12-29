package kr.or.kosa.backend.admin.controller;

import kr.or.kosa.backend.admin.dto.dashBoard.SalesStatsDto;
import kr.or.kosa.backend.admin.dto.dashBoard.SummarySection;
import kr.or.kosa.backend.admin.dto.dashBoard.UserStatsDto;
import kr.or.kosa.backend.admin.dto.response.AdminDashBoardResponseDto;
import kr.or.kosa.backend.admin.dto.response.AdminDashboardResponse;
import kr.or.kosa.backend.admin.service.AdminBatchDashBoardService;
import kr.or.kosa.backend.admin.service.AdminDashBoardService;
import kr.or.kosa.backend.commons.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminDashBoardController {
    private final AdminDashBoardService adminDashBoardService;
    private final AdminBatchDashBoardService adminBatchDashBoardService;
    public AdminDashBoardController(AdminDashBoardService adminDashBoardService, AdminBatchDashBoardService adminBatchDashBoardService) {
        this.adminDashBoardService = adminDashBoardService;
        this.adminBatchDashBoardService = adminBatchDashBoardService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AdminDashBoardResponseDto>> userSignupCount() {
        return ResponseEntity.ok(ApiResponse.success(adminDashBoardService.dashBoards()));
    }


    @GetMapping("/batch")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> userSignupCount1() {
        AdminDashboardResponse result = adminBatchDashBoardService.dashBoards();
        System.out.println("result ==>> " + result);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/dailystats")
    public ResponseEntity<ApiResponse<List<SummarySection>>> getDailyStatsByRange(
        @RequestParam("startDate") String startDate,
        @RequestParam("endDate") String endDate
    ){
        List<SummarySection> result =  adminBatchDashBoardService.getDailyStatsByRange(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/userstats")
    public ResponseEntity<ApiResponse<List<UserStatsDto>>> getRecentUserMonthlyStats(
        @RequestParam(defaultValue = "6") int limit
    ) {
        List<UserStatsDto> result = adminBatchDashBoardService.getUserMonthlyStats(limit);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/salesstats")
    public ResponseEntity<ApiResponse<List<SalesStatsDto>>> getRecentMonthlySalesStats(
        @RequestParam(defaultValue = "6") int limit
    ) {
        List<SalesStatsDto> result = adminBatchDashBoardService.getSalesMonthlyStats(limit);
        return ResponseEntity.ok(
            ApiResponse.success(result)
        );
    }



}
