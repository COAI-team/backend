package kr.or.kosa.backend.admin.service;

import kr.or.kosa.backend.admin.dto.response.PageResponseDto;
import kr.or.kosa.backend.admin.dto.response.UserFindResponseDto;

public interface AdminService {
    PageResponseDto<UserFindResponseDto> findByCondotion();

}
