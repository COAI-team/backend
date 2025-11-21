package kr.or.kosa.backend.pay.repository;


import kr.or.kosa.backend.pay.entity.Subscription;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface SubscriptionMapper {

    int insertSubscription(Subscription subscription);

    // **[추가]** 특정 사용자의 모든 구독 정보를 조회
    List<Subscription> findActiveSubscriptionsByUserId(@Param("userId") String userId);

    // **[추가]** orderId로 구독 정보 조회 (환불할 구독 찾기)
    Optional<Subscription> findSubscriptionByOrderId(@Param("orderId") String orderId);

    // **[추가]** 구독 상태를 CANCELED로 업데이트
    int updateSubscriptionStatusToCanceled(@Param("orderId") String orderId, @Param("status") String status);
}