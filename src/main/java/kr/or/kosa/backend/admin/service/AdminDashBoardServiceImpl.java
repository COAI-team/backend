package kr.or.kosa.backend.admin.service;


import kr.or.kosa.backend.admin.dto.*;
import kr.or.kosa.backend.admin.dto.UserCountSummaryDto;
import kr.or.kosa.backend.admin.dto.response.AdminDashBoardResponseDto;
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
    public AdminDashBoardResponseDto dashBoards() {
        List<UserCountSummaryDto> userCountSummary = adminDashBoardMapper.userSignUpCount();
        int todaySignUpCount = adminDashBoardMapper.todaySignUpCount();
        TodayPaymentSummaryDto todayPaymentSummary = adminDashBoardMapper.todayPaymentSummary();
        List<PaymentSummaryDto> paymentSummary = adminDashBoardMapper.paymentSummary();
        List<LanguageRankingDto> languageRanking = adminDashBoardMapper.languageRanking();
        List<AlgoSolverRankingDto> algoSolverRanking = adminDashBoardMapper.algoSolverRanking();
        List<CodeBoardStateTotalDto>  codeBoardStateTotal = adminDashBoardMapper.codeBoardStateTotal();
        List<CodeAnalysisRankingDto> codeAnalysisRanking = adminDashBoardMapper.codeAnalysisRanking();


        System.out.println("============================================================");
        System.out.println("어드민 카운트 ==>> " + userCountSummary);
        System.out.println("오늘 가입자 ==>> " + todaySignUpCount);
        System.out.println("오늘 결제 카운트  ==>> " + todayPaymentSummary.todayPaymentCount());
        System.out.println("오늘 결제 총 금액  ==>> " + todayPaymentSummary.todayPaymentTotal());
        System.out.println("페이먼츠 서머리 ==>>  " + paymentSummary);
        System.out.println("언어별 랭킹 ==>> " + languageRanking);
        System.out.println("알고리즘 푼 사람 랭킹 ==>> " +  algoSolverRanking);
        System.out.println("코드보드 관련 ==>>  " + codeBoardStateTotal);
        System.out.println("코드 분석 관련ㄷ ==>> " + codeAnalysisRanking);
        System.out.println("============================================================");
        return new AdminDashBoardResponseDto(userCountSummary, todaySignUpCount, todayPaymentSummary, paymentSummary,
            languageRanking, algoSolverRanking, codeBoardStateTotal, codeAnalysisRanking);
    }
}
