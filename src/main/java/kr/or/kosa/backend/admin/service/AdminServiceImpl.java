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
        return result;
    }

    @Override
    public void subscribeCheck(long userId) {
        System.out.println("서비스안 userId = " + userId);

        // 결제 디비확인, 토스 페이먼츠에 확인 이거 두개는 내 디비에는 없지만 토스페이먼츠에 있다면 내 데이터 베이스 업데이트
        // 결제 정보가 있다면 토스기준으로 30일 추가하자
        // 있다면 결제 정보 업데이트 하고 정보 다시 던지기



    }
}
