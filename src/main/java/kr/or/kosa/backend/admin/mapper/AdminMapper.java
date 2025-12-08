package kr.or.kosa.backend.admin.mapper;

import kr.or.kosa.backend.admin.dto.response.AdminUserDetailResponseDto;
import kr.or.kosa.backend.admin.dto.response.UserFindResponseDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminMapper {
    List<UserFindResponseDto> findCondition(
        @Param("limit") int limit,
        @Param("offset") int offset,
        @Param("userEmail") String userEmail,
        @Param("filter") Object filter,
        @Param("sortField") String sortField,
        @Param("sortOrder") String sortOrder
    );
    int totalCount(@Param("filter") Object filter, @Param("userEmail") String userEmail);
    AdminUserDetailResponseDto findOneUserByUseerId(@Param("userId") long userId);
}
