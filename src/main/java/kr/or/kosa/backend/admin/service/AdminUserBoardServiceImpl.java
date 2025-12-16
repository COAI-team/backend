package kr.or.kosa.backend.admin.service;

import kr.or.kosa.backend.admin.dto.response.AlgoBoardDetailResponseDto;
import kr.or.kosa.backend.admin.dto.BoardItems;
import kr.or.kosa.backend.admin.dto.CodeBoardAnalysisDetailDto;
import kr.or.kosa.backend.admin.dto.request.UserBoardSearchConditionRequestDto;
import kr.or.kosa.backend.admin.dto.response.AdminCodeBoardDetailResponseDto;
import kr.or.kosa.backend.admin.dto.response.AdminFreeBoardDetailResponseDto;
import kr.or.kosa.backend.admin.dto.response.PageResponseDto;
import kr.or.kosa.backend.admin.mapper.AdminUserBoardMapper;
import kr.or.kosa.backend.codenose.dto.GithubFileDTO;
import kr.or.kosa.backend.codenose.service.GithubService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AdminUserBoardServiceImpl implements AdminUserBoardService {
    private final AdminUserBoardMapper adminUserBoardMapper;
    private final GithubService githubService;


    public AdminUserBoardServiceImpl(AdminUserBoardMapper adminUserBoardMapper, GithubService githubService) {
        this.adminUserBoardMapper = adminUserBoardMapper;
        this.githubService = githubService;
    }

    @Override
    public PageResponseDto<BoardItems> getUserBoards(UserBoardSearchConditionRequestDto userBoardSearchConditionRequestDtos) {
        List<BoardItems> boardItems = new ArrayList<>();
        boardItems = adminUserBoardMapper.userBoards(userBoardSearchConditionRequestDtos);

        int userBoardCount = adminUserBoardMapper.countUserBoards(userBoardSearchConditionRequestDtos);
        return new PageResponseDto<>(boardItems,userBoardSearchConditionRequestDtos.page(), userBoardSearchConditionRequestDtos.size(), userBoardCount);
    }

    @Override
    public AlgoBoardDetailResponseDto getAlgoBoard(long boardId) {

        return adminUserBoardMapper.findOneAlgoBoardByBoardId(boardId);
    }

    public AdminCodeBoardDetailResponseDto getOneCodeBoard(long boardId) {
        CodeBoardAnalysisDetailDto codeBoardDetail = adminUserBoardMapper.findOneCodeBoardByBoardId(boardId);
        GithubFileDTO fileDTO = githubService.getFileContent(codeBoardDetail.userId(), codeBoardDetail.getOwner(), codeBoardDetail.getRepo(), codeBoardDetail.filePath());

        return new AdminCodeBoardDetailResponseDto(codeBoardDetail, fileDTO.getFileContent());
    }

    @Override
    public AdminFreeBoardDetailResponseDto getOneFreeBoard(long boardId) {
        return adminUserBoardMapper.findOnefreeBoardByBoardId(boardId);
    }
}
