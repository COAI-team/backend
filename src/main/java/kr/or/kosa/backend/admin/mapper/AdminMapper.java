package kr.or.kosa.backend.admin.mapper;

import kr.or.kosa.backend.admin.dto.response.UserFindResponseDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminMapper {
    List<UserFindResponseDto> findCondition(
        @Param("limit") int limit,
        @Param("offset") int offset,
        @Param("userRole") String userRole,
        @Param("userEmail") String userEmail
    );
    int totalCount(@Param("userRole") String userRole, @Param("userEmail") String userEmail);
}
