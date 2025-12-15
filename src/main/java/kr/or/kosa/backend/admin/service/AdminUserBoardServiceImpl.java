package kr.or.kosa.backend.admin.service;

import kr.or.kosa.backend.admin.dto.BoardItem;
import kr.or.kosa.backend.admin.dto.request.UserBoardSearchConditionRequestDto;
import kr.or.kosa.backend.admin.dto.response.PageResponseDto;
import kr.or.kosa.backend.admin.enums.BoardType;
import kr.or.kosa.backend.admin.exception.AdminErrorCode;
import kr.or.kosa.backend.admin.mapper.AdminUserBoardMapper;
import kr.or.kosa.backend.commons.exception.custom.CustomSystemException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AdminUserBoardServiceImpl implements AdminUserBoardService {
    private final AdminUserBoardMapper adminUserBoardMapper;

    public AdminUserBoardServiceImpl(AdminUserBoardMapper adminUserBoardMapper) {
        this.adminUserBoardMapper = adminUserBoardMapper;
    }

    @Override
    public PageResponseDto<BoardItem> getUserBoards(UserBoardSearchConditionRequestDto userBoardSearchConditionRequestDtos) {
        List<BoardItem> boardItems = new ArrayList<>();
        boardItems = adminUserBoardMapper.userBoards(userBoardSearchConditionRequestDtos);

        int userBoardCount = adminUserBoardMapper.countUserBoards(userBoardSearchConditionRequestDtos);
        PageResponseDto<BoardItem> pageResponseDto = new PageResponseDto<>(boardItems,userBoardSearchConditionRequestDtos.page(), userBoardSearchConditionRequestDtos.size(), userBoardCount);
        return pageResponseDto;
    }

    @Override
    public void getOneUserBoard(long boardId, String boardType) {
        BoardType type;
        try {
            type = BoardType.valueOf(boardType.toLowerCase());
        } catch (IllegalArgumentException e) {
            throw new CustomSystemException(AdminErrorCode.ADMIN_BOARD_TYPE);
        }

        switch (type) {
            case algo -> System.out.println("알고 게시판 처리");
            case code -> System.out.println("코드 게시판 처리");
            case free -> System.out.println("자유 게시판 처리");
        }

    }


}
