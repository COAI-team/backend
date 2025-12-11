package kr.or.kosa.backend.admin.service;

import kr.or.kosa.backend.admin.dto.UserPaymentDetailDto;
import kr.or.kosa.backend.admin.dto.request.SearchConditionRequestDto;
import kr.or.kosa.backend.admin.dto.response.AdminUserDetailResponseDto;
import kr.or.kosa.backend.admin.dto.response.PageResponseDto;
import kr.or.kosa.backend.admin.dto.response.UserFindResponseDto;
import kr.or.kosa.backend.admin.mapper.AdminUserMapper;
import kr.or.kosa.backend.pay.dto.TossConfirmResult;
import kr.or.kosa.backend.pay.service.TossPaymentsClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminServiceImpl implements AdminUserService {

    private final AdminUserMapper adminMapper;
    private final TossPaymentsClient tossPaymentsClient;

    public AdminServiceImpl(AdminUserMapper adminMapper,  TossPaymentsClient tossPaymentsClient) {
        this.adminMapper = adminMapper;
        this.tossPaymentsClient = tossPaymentsClient;
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
        AdminUserDetailResponseDto result = adminMapper.findOneUserByUserId(userId);
        return result;
    }

    @Override
    public boolean subscribeCheck(long userId) {
        System.out.println("서비스안 userId = " + userId);
        // 내 디비 조회
        UserPaymentDetailDto resultMyDB = adminMapper.findOneUserPaymentDetailByUserId(userId);
        // 결제는 되었는데 구독이 설정이 안된거라면?
        if(resultMyDB != null){ // 디비에는 있다
            int originalAmount = resultMyDB.amount().intValue(); // 원래 금액
            int totalAmount = resultMyDB.amount().intValue() + resultMyDB.usedPoint().intValue(); // 포인트로
            TossConfirmResult tr = tossPaymentsClient.confirmPayment(resultMyDB.paymentKey(), resultMyDB.order_id(), resultMyDB.amount().longValue());

            // 여기서 토스페이먼츠 가자
            boolean tossPaymentsDB = true;
            // 포인트와 내 결제금액이 같다면?
            if(tossPaymentsDB){
                if(originalAmount == totalAmount || originalAmount == resultMyDB.amount().intValue()){
                    // 포인트 히스토리 확인
                    if(!adminMapper.checkUsedPoint(userId, resultMyDB.order_id(), resultMyDB.usedPoint())){ // 없을때만
                        // 이때 만들어야지 토스페이먼츠랑 내디비에있으니까
                        return true;
                    }
                }
            }else { //여기는 토스페이먼츠에 데이터가 없으니까 결제가 잘못된거다. 그냥 잘못된 결제로 가자 결제 내역 삭제로해야겠지?

            }
        }
        return false;

        // 결제 디비확인, 토스 페이먼츠에 확인 이거 두개는 내 디비에는 없지만 토스페이먼츠에 있다면 내 데이터 베이스 업데이트
        // 결제 정보가 있다면 토스기준으로 30일 추가하자
        // 있다면 결제 정보 업데이트 하고 정보 다시 던지기



    }
}
