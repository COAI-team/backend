package kr.or.kosa.backend.pay.repository;

import kr.or.kosa.backend.pay.entity.Subscription;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface SubscriptionMapper {

    int insertSubscription(Subscription subscription);

    // 특정 사용자의 활성 구독 목록
    List<Subscription> findActiveSubscriptionsByUserId(@Param("userId") String userId);

    // orderId로 구독 정보 조회
    Optional<Subscription> findSubscriptionByOrderId(@Param("orderId") String orderId);

    // 구독 상태를 CANCELED 등으로 변경
    int updateSubscriptionStatusToCanceled(@Param("orderId") String orderId,
                                           @Param("status") String status);

    // 🔥 추가: 해당 유저의 "특정 타입(BASIC/PRO)" 중 가장 최신 ACTIVE 한 건
    Optional<Subscription> findLatestActiveSubscriptionByUserIdAndType(
            @Param("userId") String userId,
            @Param("subscriptionType") String subscriptionType
    );
}