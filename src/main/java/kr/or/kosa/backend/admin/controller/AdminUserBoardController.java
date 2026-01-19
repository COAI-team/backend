package kr.or.kosa.backend.admin.controller;


import kr.or.kosa.backend.admin.dto.BoardItems;
import kr.or.kosa.backend.admin.dto.request.DeleteBoardRequestDto;
import kr.or.kosa.backend.admin.dto.request.UserBoardSearchConditionRequestDto;
import kr.or.kosa.backend.admin.dto.response.AdminCodeBoardDetailResponseDto;
import kr.or.kosa.backend.admin.dto.response.AdminFreeBoardDetailResponseDto;
import kr.or.kosa.backend.admin.dto.response.AlgoBoardDetailResponseDto;
import kr.or.kosa.backend.admin.dto.response.PageResponseDto;
import kr.or.kosa.backend.admin.exception.AdminErrorCode;
import kr.or.kosa.backend.admin.service.AdminUserBoardService;
import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import kr.or.kosa.backend.commons.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
public class  AdminUserBoardController {
    private final AdminUserBoardService adminUserBoardService;

    public AdminUserBoardController(AdminUserBoardService adminUserBoardService) {
        this.adminUserBoardService = adminUserBoardService;
    }

    @GetMapping("/userboards")
    public ResponseEntity<ApiResponse<PageResponseDto<BoardItems>>> adminUserBoards(
        @ModelAttribute UserBoardSearchConditionRequestDto userBoardSearchConditionRequestDto
        ) {
        PageResponseDto<BoardItems> result = adminUserBoardService.getUserBoards(userBoardSearchConditionRequestDto);
        return ResponseEntity.ok(ApiResponse.success(result));
    }


    @GetMapping("/boarddetail/algo/{boardId}")
    public ResponseEntity<ApiResponse<AlgoBoardDetailResponseDto>> algoBoardDitail(
        @PathVariable("boardId") long boardId
    ){
        AlgoBoardDetailResponseDto result = adminUserBoardService.getAlgoBoard(boardId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/boarddetail/code/{boardId}")
    public ResponseEntity<ApiResponse<AdminCodeBoardDetailResponseDto>> codeBoardDitail(
        @PathVariable("boardId") long boardId
    ){
        AdminCodeBoardDetailResponseDto result = adminUserBoardService.getOneCodeBoard(boardId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/boarddetail/free/{boardId}")
    public ResponseEntity<ApiResponse<AdminFreeBoardDetailResponseDto>> freeBoardDitail(
        @PathVariable("boardId") long boardId
    ){
        AdminFreeBoardDetailResponseDto result = adminUserBoardService.getOneFreeBoard(boardId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @DeleteMapping("/boarddelte")
    public ResponseEntity<ApiResponse<Void>> deleteBoard(
        @RequestBody DeleteBoardRequestDto deleteBoardRequestDto
        ){
        adminUserBoardService.deleteBoard(deleteBoardRequestDto);
        return null;
    }


}
