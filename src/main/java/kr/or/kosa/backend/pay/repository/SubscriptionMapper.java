package kr.or.kosa.backend.pay.repository;

import kr.or.kosa.backend.pay.entity.Subscription;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface SubscriptionMapper {

    int insertSubscription(Subscription subscription);

    List<Subscription> findActiveSubscriptionsByUserId(@Param("userId") String userId);

    Optional<Subscription> findSubscriptionByOrderId(@Param("orderId") String orderId);

    int updateSubscriptionStatusToCanceled(@Param("orderId") String orderId,
                                           @Param("status") String status);

    Optional<Subscription> findLatestActiveSubscriptionByUserIdAndType(
            @Param("userId") String userId,
            @Param("subscriptionType") String subscriptionType
    );

    int expireSubscriptionsByUserId(@Param("userId") String userId);
}
