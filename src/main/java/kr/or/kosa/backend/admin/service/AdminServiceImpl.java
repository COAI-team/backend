package kr.or.kosa.backend.admin.service;

import kr.or.kosa.backend.admin.dto.request.SearchConditionRequestDto;
import kr.or.kosa.backend.admin.dto.response.PageResponseDto;
import kr.or.kosa.backend.admin.dto.response.UserFindResponseDto;
import kr.or.kosa.backend.admin.mapper.AdminMapper;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class AdminServiceImpl implements AdminService {

    private final AdminMapper adminMapper;

    public AdminServiceImpl(AdminMapper adminMapper) {
        this.adminMapper = adminMapper;
    }

    @Override
    public PageResponseDto<UserFindResponseDto> findByCondotion(SearchConditionRequestDto req) {
        List<UserFindResponseDto> userFindResponseDto = adminMapper.findCondition(req.size(), req.getOffset(), req.userRole(), req.userEmail());
        int totalCount = adminMapper.totalCount(req.userRole(),req.userEmail());

        PageResponseDto<UserFindResponseDto> pageResponseDto = new PageResponseDto<>(userFindResponseDto, req.page(), req.size(), totalCount);
        for(UserFindResponseDto dto : pageResponseDto.content()){
            System.out.println(dto.getUserEmail());
        }
        return pageResponseDto;
    }
    }
