package kr.or.kosa.backend.admin.service;

import kr.or.kosa.backend.admin.dto.UserPaymentDetailDto;
import kr.or.kosa.backend.admin.dto.request.SearchConditionRequestDto;
import kr.or.kosa.backend.admin.dto.response.AdminUserDetailResponseDto;
import kr.or.kosa.backend.admin.dto.response.PageResponseDto;
import kr.or.kosa.backend.admin.dto.response.UserFindResponseDto;
import kr.or.kosa.backend.admin.exception.AdminErrorCode;
import kr.or.kosa.backend.admin.mapper.AdminUserMapper;
import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import kr.or.kosa.backend.pay.dto.TossConfirmResult;
import kr.or.kosa.backend.pay.entity.Subscription;
import kr.or.kosa.backend.pay.repository.SubscriptionMapper;
import kr.or.kosa.backend.pay.service.TossPaymentsClient;
import kr.or.kosa.backend.users.domain.Users;
import kr.or.kosa.backend.users.dto.UserResponseDto;
import kr.or.kosa.backend.users.mapper.UserMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class AdminServiceImpl implements AdminUserService {

    private final AdminUserMapper adminMapper;
    private final TossPaymentsClient tossPaymentsClient;
    private final SubscriptionMapper subscriptionMapper;
    private final UserMapper userMapper;

    public AdminServiceImpl(AdminUserMapper adminMapper, TossPaymentsClient tossPaymentsClient, SubscriptionMapper subscriptionMapper, UserMapper userMapper) {
        this.adminMapper = adminMapper;
        this.tossPaymentsClient = tossPaymentsClient;
        this.subscriptionMapper = subscriptionMapper;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<UserFindResponseDto> findByCondotion(SearchConditionRequestDto req) {
        List<UserFindResponseDto> userFindResponseDto = adminMapper.findCondition(req.size(), req.getOffset(), req.userEmail(), req.roleFilter(), req.sortField(), req.sortOrder(), req.statusFilter());
        int totalCount = adminMapper.totalCount(req.statusFilter(), req.roleFilter(),req.userEmail());

        return new PageResponseDto<>(userFindResponseDto, req.page(), req.size(), totalCount);
    }

    @Override
    public AdminUserDetailResponseDto userDetail(long userId) {
        AdminUserDetailResponseDto result = adminMapper.findOneUserByUserId(userId);
        return result;
    }

    @Override
    public boolean subscribeCheck(long userId) {
        // 내 디비 조회
        UserPaymentDetailDto resultMyDB = adminMapper.findOneUserPaymentDetailByUserId(userId);
        // 결제는 되었는데 구독이 설정이 안된거라면?
        if(resultMyDB != null){ // 디비에는 있다
            int originalAmount = resultMyDB.amount().intValue(); // 원래 금액
            int totalAmount = resultMyDB.amount().intValue() + resultMyDB.usedPoint().intValue(); // 포인트로
            // 여기서 토스페이먼츠 가자
            boolean tossPaymentsDB = tossPaymentsClient.getPayment(resultMyDB.paymentKey());;
            if(tossPaymentsDB){
                if(originalAmount == totalAmount || originalAmount == resultMyDB.amount().intValue()){
                    LocalDateTime now = LocalDateTime.now();

                    subscriptionMapper.insertSubscription(Subscription.builder()
                        .userId(resultMyDB.userId())
                        .orderId(resultMyDB.orderId())
                        .subscriptionType(resultMyDB.planCode())
                        .startDate(now)
                        .endDate(now.plusMonths(1))
                        .status("ACTIVE")
                        .build());
                    // 포인트 히스토리 확인
//                    if(!adminMapper.checkUsedPoint(userId, resultMyDB.order_id(), resultMyDB.usedPoint())){ // 없을때만
//                        // 이때 만들어야지 토스페이먼츠랑 내디비에있으니까
//                        return true;
//                    }
                    return true;
                }
            }else { //여기는 토스페이먼츠에 데이터가 없으니까 결제가 잘못된거다. 그냥 잘못된 결제로 가자 결제 내역 삭제로해야겠지?
                int result = adminMapper.deleteSubscription(resultMyDB.userId(), resultMyDB.orderId(), resultMyDB.planCode());
                if(result == 1){
                    return false;
                }
            }
        }
        return false;
    }

    @Override
    @Transactional
    public Users banUser(long userId) {
        int result = adminMapper.banUser(userId);

        if(result == 1){
            return userMapper.findById(userId);
        }
        throw new CustomBusinessException(AdminErrorCode.ADMIN_SIZE);
   }
}
