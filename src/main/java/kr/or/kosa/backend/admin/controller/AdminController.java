package kr.or.kosa.backend.admin.controller;

import kr.or.kosa.backend.admin.dto.request.SearchConditionRequestDto;
import kr.or.kosa.backend.admin.dto.response.PageResponseDto;
import kr.or.kosa.backend.admin.dto.response.UserFindResponseDto;
import kr.or.kosa.backend.admin.service.AdminService;
import kr.or.kosa.backend.commons.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
public class AdminController {
    private final AdminService adminService;
    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResponseDto<UserFindResponseDto>>> findUserByCondition(
       @ModelAttribute SearchConditionRequestDto req
    ){
        PageResponseDto<UserFindResponseDto> result = adminService.findByCondotion(req);
        return  ResponseEntity.ok(ApiResponse.success(result));
    }
}
