package kr.or.kosa.backend.admin.service;

import kr.or.kosa.backend.admin.dto.request.SearchConditionRequestDto;
import kr.or.kosa.backend.admin.dto.response.AdminUserDetailResponseDto;
import kr.or.kosa.backend.admin.dto.response.PageResponseDto;
import kr.or.kosa.backend.admin.dto.response.UserFindResponseDto;

public interface AdminUserService {
    PageResponseDto<UserFindResponseDto> findByCondotion(SearchConditionRequestDto req);
    AdminUserDetailResponseDto userDetail(long userId);
    boolean subscribeCheck(long userId);
}
