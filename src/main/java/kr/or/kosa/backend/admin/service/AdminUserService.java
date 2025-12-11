package kr.or.kosa.backend.admin.service;

import kr.or.kosa.backend.admin.dto.request.SearchConditionRequestDto;
import kr.or.kosa.backend.admin.dto.response.AdminUserDetailResponseDto;
import kr.or.kosa.backend.admin.dto.response.PageResponseDto;
import kr.or.kosa.backend.admin.dto.response.UserFindResponseDto;
import kr.or.kosa.backend.users.domain.Users;
import kr.or.kosa.backend.users.dto.UserResponseDto;

public interface AdminUserService {
    PageResponseDto<UserFindResponseDto> findByCondotion(SearchConditionRequestDto req);
    AdminUserDetailResponseDto userDetail(long userId);
    boolean subscribeCheck(long userId);
    Users banUser(long userId);

}
