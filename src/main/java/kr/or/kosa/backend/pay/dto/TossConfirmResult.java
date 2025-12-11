package kr.or.kosa.backend.pay.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Builder
@ToString
public class TossConfirmResult {
    private final String status;        // 예: DONE
    private final String payMethod;     // 내부 코드 (CARD, EASY_TOSS 등)
    private final String rawJson;       // PG 전체 응답 JSON
    private final String cardCompany;   // 카드 발급사 코드
    private final String approveNo;     // 승인 번호
    private final LocalDateTime approvedAt; // KST 기준 승인 시각
}