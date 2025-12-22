package kr.or.kosa.backend.admin.service;

import kr.or.kosa.backend.admin.dto.request.DeleteBoardRequestDto;
import kr.or.kosa.backend.admin.dto.response.AlgoBoardDetailResponseDto;
import kr.or.kosa.backend.admin.dto.BoardItems;
import kr.or.kosa.backend.admin.dto.request.UserBoardSearchConditionRequestDto;
import kr.or.kosa.backend.admin.dto.response.AdminCodeBoardDetailResponseDto;
import kr.or.kosa.backend.admin.dto.response.AdminFreeBoardDetailResponseDto;
import kr.or.kosa.backend.admin.dto.response.PageResponseDto;

public interface AdminUserBoardService {
    PageResponseDto<BoardItems> getUserBoards(UserBoardSearchConditionRequestDto userBoardSearchConditionRequestDto);
    AlgoBoardDetailResponseDto getAlgoBoard(long boardId);
    AdminCodeBoardDetailResponseDto getOneCodeBoard(long boardId);
    AdminFreeBoardDetailResponseDto getOneFreeBoard(long boardId);
    int deleteBoard(DeleteBoardRequestDto  deleteBoardRequestDto);
}
