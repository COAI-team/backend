package kr.or.kosa.backend.admin.mapper;

import kr.or.kosa.backend.admin.dto.UserPaymentDetailDto;
import kr.or.kosa.backend.admin.dto.response.AdminUserDetailResponseDto;
import kr.or.kosa.backend.admin.dto.response.UserFindResponseDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface AdminUserMapper {
    List<UserFindResponseDto> findCondition(
        @Param("limit") int limit,
        @Param("offset") int offset,
        @Param("userEmail") String userEmail,
        @Param("roleFilter") Object roleFilter,
        @Param("sortField") String sortField,
        @Param("sortOrder") String sortOrder,
        @Param("statusFilter") Object statusFilter
    );
    int totalCount(@Param("statusFilter") Object statusFilter, @Param("roleFilter") Object roleFilter, @Param("userEmail") String userEmail);
    AdminUserDetailResponseDto findOneUserByUserId(@Param("userId") long userId);
    UserPaymentDetailDto findOneUserPaymentDetailByUserId(@Param("userId") long userId);
    boolean checkUsedPoint(@Param("userId") long userId, @Param("orderId") String orderId, @Param("usedPoint")BigDecimal usedPoint);
    int deleteSubscription(@Param("userId") long userId,
                           @Param("orderId") String orderId,
                           @Param("subscriptionType") String type);
    int banUser(@Param("userId") long userId);
}
