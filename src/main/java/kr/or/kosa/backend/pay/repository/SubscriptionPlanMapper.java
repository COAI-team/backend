package kr.or.kosa.backend.pay.repository;

import kr.or.kosa.backend.pay.entity.SubscriptionPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SubscriptionPlanMapper {
    SubscriptionPlan findActiveByPlanCode(@Param("planCode") String planCode);
}
