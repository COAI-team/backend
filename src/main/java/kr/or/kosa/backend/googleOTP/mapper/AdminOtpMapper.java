package kr.or.kosa.backend.googleOTP.mapper;

import kr.or.kosa.backend.googleOTP.dto.AdminOtpDto;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminOtpMapper {
    AdminOtpDto findByUserId(Long userId);

    int upsert(AdminOtpDto adminOtp);

    int updateSecret(AdminOtpDto adminOtp);

    int enableOtp(Long userId);
    void disableOtp(Long userId);

}
