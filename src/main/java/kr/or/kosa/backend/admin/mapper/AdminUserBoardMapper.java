package kr.or.kosa.backend.admin.mapper;

import kr.or.kosa.backend.admin.dto.BoardItem;
import kr.or.kosa.backend.admin.dto.request.UserBoardSearchConditionRequestDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminUserBoardMapper {
    List<BoardItem> userBoards(@Param("cond")
                               UserBoardSearchConditionRequestDto cond);
    int countUserBoards(@Param("cond") UserBoardSearchConditionRequestDto cond);

}
