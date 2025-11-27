package kr.or.kosa.backend.pay.repository;

import kr.or.kosa.backend.pay.entity.PointHistory;
import kr.or.kosa.backend.pay.entity.UserPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.Optional;

@Mapper
public interface PointMapper {

    Optional<UserPoint> findUserPointByUserId(@Param("userId") String userId);

    int insertUserPoint(UserPoint userPoint);

    /**
     * 포인트 사용 (잔액 차감). 잔액 부족 시 0 row 업데이트되도록 구현.
     */
    int usePoint(@Param("userId") String userId,
                 @Param("amount") BigDecimal amount);

    /**
     * 포인트 환불(복구).
     */
    int refundPoint(@Param("userId") String userId,
                    @Param("amount") BigDecimal amount);

    int insertPointHistory(PointHistory history);
}
