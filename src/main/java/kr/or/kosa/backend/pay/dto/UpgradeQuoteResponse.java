package kr.or.kosa.backend.pay.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpgradeQuoteResponse {

    // 업그레이드 상황인지 여부 (BASIC → PRO 인 상태인지)
    private boolean upgrade;

    // from / to 플랜
    private String fromPlan;   // 예: "BASIC"
    private String toPlan;     // 예: "PRO"

    // 사용 / 남은 일수
    private long usedDays;
    private long remainingDays;

    // 이번에 결제해야 할 추가 금액 (원)
    private int extraAmount;

    // 기존 BASIC 구독 종료일 (표시용)
    private String basicEndDate;
}
