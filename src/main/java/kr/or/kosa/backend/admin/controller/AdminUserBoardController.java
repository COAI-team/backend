package kr.or.kosa.backend.admin.controller;


import kr.or.kosa.backend.admin.dto.BoardItem;
import kr.or.kosa.backend.admin.dto.request.UserBoardSearchConditionRequestDto;
import kr.or.kosa.backend.admin.dto.response.PageResponseDto;
import kr.or.kosa.backend.admin.service.AdminUserBoardService;
import kr.or.kosa.backend.commons.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminUserBoardController {
    private final AdminUserBoardService adminUserBoardService;

    public AdminUserBoardController(AdminUserBoardService adminUserBoardService) {
        this.adminUserBoardService = adminUserBoardService;
    }

    @GetMapping("/userboards")
    public ResponseEntity<ApiResponse<PageResponseDto<BoardItem>>> adminUserBoards(
        @ModelAttribute UserBoardSearchConditionRequestDto userBoardSearchConditionRequestDto
        ) {
        PageResponseDto<BoardItem> result = adminUserBoardService.getUserBoards(userBoardSearchConditionRequestDto);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
