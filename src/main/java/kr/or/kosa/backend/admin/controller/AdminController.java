package kr.or.kosa.backend.admin.controller;

import kr.or.kosa.backend.admin.dto.request.SearchConditionRequestDto;
import kr.or.kosa.backend.admin.dto.response.AdminUserDetailResponseDto;
import kr.or.kosa.backend.admin.dto.response.PageResponseDto;
import kr.or.kosa.backend.admin.dto.response.UserFindResponseDto;
import kr.or.kosa.backend.admin.service.AdminUserService;
import kr.or.kosa.backend.commons.response.ApiResponse;
import kr.or.kosa.backend.users.domain.Users;
import kr.or.kosa.backend.users.dto.UserResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/admin")
public class AdminController {
    private final AdminUserService adminService;
    public AdminController(AdminUserService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResponseDto<UserFindResponseDto>>> findUserByCondition(
       @ModelAttribute SearchConditionRequestDto req
    ){
        System.out.println("req users ==>> " + req);
        PageResponseDto<UserFindResponseDto> result = adminService.findByCondotion(req);
        return  ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/userdetail/{userId}")
    public ResponseEntity<ApiResponse<AdminUserDetailResponseDto>> findOneUser(
        @PathVariable("userId") Long userId
    ) {
        AdminUserDetailResponseDto result = adminService.userDetail(userId);
        System.out.println("컨트롤러에서 result => " + result.toString());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/subscribecheck/{userId}")
    public ResponseEntity<ApiResponse<Boolean>> subscribeCheck(
        @PathVariable("userId") Long userId
    ) {
        boolean result = adminService.subscribeCheck(userId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/banuser/{userId}")
    public ResponseEntity<ApiResponse<LocalDateTime>> banUser(
        @PathVariable("userId") Long userId
    ){
        return ResponseEntity.ok(ApiResponse.success(adminService.banUser(userId).getUserDeletedat()));
    }
}
