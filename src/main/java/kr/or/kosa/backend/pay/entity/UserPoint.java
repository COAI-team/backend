package kr.or.kosa.backend.pay.entity;

import lombok.*;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@ToString
public class UserPoint {

    private String userId;       // PK
    private int balance;         // 현재 잔액
    private LocalDateTime updatedAt; // 마지막 변경 시각
}