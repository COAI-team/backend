package kr.or.kosa.backend.admin.service;


import kr.or.kosa.backend.admin.dto.PaymentSummaryDto;
import kr.or.kosa.backend.admin.dto.TodayPaymentSummaryDto;
import kr.or.kosa.backend.admin.dto.response.AdminDashBoardUserCountResponseDto;
import kr.or.kosa.backend.admin.mapper.AdminDashBoardMapper;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class AdminDashBoardServiceImpl implements AdminDashBoardService {
    private final AdminDashBoardMapper adminDashBoardMapper;

    public AdminDashBoardServiceImpl(AdminDashBoardMapper adminDashBoardMapper) {
        this.adminDashBoardMapper = adminDashBoardMapper;
    }
    @Override
    public int dashBoards() {
        List<AdminDashBoardUserCountResponseDto> aa = adminDashBoardMapper.userSignUpCount();
        int todayCount = adminDashBoardMapper.todaySignUpCount();
        TodayPaymentSummaryDto todayPaymentSummary = adminDashBoardMapper.todayPaymentSummary();
        List<PaymentSummaryDto> paymentSummary = adminDashBoardMapper.paymentSummary();

        System.out.println("============================================================");
        System.out.println("어드민 카운트 ==>> " + aa);
        System.out.println("오늘 가입자 ==>> " + todayCount);
        System.out.println("오늘 결제 카운트  ==>> " + todayPaymentSummary.todayPaymentCount());
        System.out.println("오늘 결제 총 금액  ==>> " + todayPaymentSummary.todayPaymentTotal());
        System.out.println("페이먼츠 서머리 ==>>  " + paymentSummary);
        System.out.println("============================================================");
        return 0;
    }
}
