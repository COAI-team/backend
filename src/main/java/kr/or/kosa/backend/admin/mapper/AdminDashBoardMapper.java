package kr.or.kosa.backend.admin.mapper;

import kr.or.kosa.backend.admin.dto.PaymentSummaryDto;
import kr.or.kosa.backend.admin.dto.TodayPaymentSummaryDto;
import kr.or.kosa.backend.admin.dto.response.AdminDashBoardUserCountResponseDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AdminDashBoardMapper {
    List<AdminDashBoardUserCountResponseDto> userSignUpCount();
    int todaySignUpCount();
    TodayPaymentSummaryDto todayPaymentSummary();
    List<PaymentSummaryDto> paymentSummary();
}
