package kr.or.kosa.backend.admin.mapper;

import kr.or.kosa.backend.admin.dto.response.AdminDashBoardUserCountResponseDto;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminDashBoardMapper {
    AdminDashBoardUserCountResponseDto user();
}
