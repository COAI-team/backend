package kr.or.kosa.backend.admin.controller;

import kr.or.kosa.backend.admin.dto.response.AdminDashBoardResponseDto;
import kr.or.kosa.backend.admin.service.AdminDashBoardService;
import kr.or.kosa.backend.commons.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminDashBoardController {
    private final AdminDashBoardService adminDashBoardService;
    public AdminDashBoardController(AdminDashBoardService adminDashBoardService) {
        this.adminDashBoardService = adminDashBoardService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AdminDashBoardResponseDto>> userSignupCount() {
        return ResponseEntity.ok(ApiResponse.success(adminDashBoardService.dashBoards()));
    }

}
