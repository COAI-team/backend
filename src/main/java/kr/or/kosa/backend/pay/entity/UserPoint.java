package kr.or.kosa.backend.pay.entity;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@ToString
public class UserPoint {

    private Long userId;       // PK
    private BigDecimal balance;         // 현재 잔액
    private LocalDateTime updatedAt; // 마지막 변경 시각
}
