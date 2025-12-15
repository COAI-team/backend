package kr.or.kosa.backend.admin.service;

import kr.or.kosa.backend.admin.dto.BoardItem;
import kr.or.kosa.backend.admin.dto.request.UserBoardSearchConditionRequestDto;
import kr.or.kosa.backend.admin.dto.response.PageResponseDto;

import java.util.List;

public interface AdminUserBoardService {
    PageResponseDto<BoardItem> getUserBoards(UserBoardSearchConditionRequestDto userBoardSearchConditionRequestDto);
    void getOneUserBoard(long boardId, String boardType);
}
