package kr.or.kosa.backend.admin.service;

import kr.or.kosa.backend.admin.dto.request.SearchConditionRequestDto;
import kr.or.kosa.backend.admin.dto.response.AdminUserDetailResponseDto;
import kr.or.kosa.backend.admin.dto.response.PageResponseDto;
import kr.or.kosa.backend.admin.dto.response.UserFindResponseDto;
import kr.or.kosa.backend.admin.mapper.AdminMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminServiceImpl implements AdminService {

    private final AdminMapper adminMapper;

    public AdminServiceImpl(AdminMapper adminMapper) {
        this.adminMapper = adminMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<UserFindResponseDto> findByCondotion(SearchConditionRequestDto req) {
        List<UserFindResponseDto> userFindResponseDto = adminMapper.findCondition(req.size(), req.getOffset(), req.userEmail(), req.filter(), req.sortField(), req.sortOrder());
        int totalCount = adminMapper.totalCount(req.filter(),req.userEmail());

        return new PageResponseDto<>(userFindResponseDto, req.page(), req.size(), totalCount);
    }

    @Override
    public AdminUserDetailResponseDto userDetail(long userId) {
        AdminUserDetailResponseDto result = adminMapper.findOneUserByUseerId(userId);
        return null;
    }
}
