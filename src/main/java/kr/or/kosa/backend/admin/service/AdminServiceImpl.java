package kr.or.kosa.backend.admin.service;

import kr.or.kosa.backend.admin.dto.response.PageResponseDto;
import kr.or.kosa.backend.admin.dto.response.UserFindResponseDto;
import kr.or.kosa.backend.admin.mapper.AdminMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class AdminServiceImpl implements AdminService {

    private final AdminMapper adminMapper;

    public AdminServiceImpl(AdminMapper adminMapper) {
        this.adminMapper = adminMapper;
    }

    @Override
    public PageResponseDto<UserFindResponseDto> findByCondotion() {
        List<UserFindResponseDto> userFindResponseDto = adminMapper.findCondition(10, 1, "", "");
        int totalCount = adminMapper.totalCount("","");

        PageResponseDto<UserFindResponseDto> pageResponseDto = new PageResponseDto<>(userFindResponseDto, 1, 10, totalCount);
        for(UserFindResponseDto dto : pageResponseDto.content()){
            System.out.println(dto.getUserEmail());
        }
        return null;
    }
}
