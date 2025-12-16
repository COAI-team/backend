package kr.or.kosa.backend.admin.mapper;

import kr.or.kosa.backend.admin.dto.response.AlgoBoardDetailResponseDto;
import kr.or.kosa.backend.admin.dto.BoardItems;
import kr.or.kosa.backend.admin.dto.CodeBoardAnalysisDetailDto;
import kr.or.kosa.backend.admin.dto.request.UserBoardSearchConditionRequestDto;
import kr.or.kosa.backend.admin.dto.response.AdminFreeBoardDetailResponseDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminUserBoardMapper {
    List<BoardItems> userBoards(@Param("cond")
                               UserBoardSearchConditionRequestDto cond);
    int countUserBoards(@Param("cond") UserBoardSearchConditionRequestDto cond);

    AlgoBoardDetailResponseDto findOneAlgoBoardByBoardId(@Param("boardId") long boardId);
    CodeBoardAnalysisDetailDto findOneCodeBoardByBoardId(@Param("boardId") long boardId);
    AdminFreeBoardDetailResponseDto findOnefreeBoardByBoardId(@Param("boardId") long boardId);
}
