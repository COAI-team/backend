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


        return new AdminDashBoardResponseDto(userCountSummary, todaySignUpCount, todayPaymentSummary, paymentSummary,
            languageRanking, algoSolverRanking, codeBoardStateTotal, codeAnalysisRanking);
    }
}
