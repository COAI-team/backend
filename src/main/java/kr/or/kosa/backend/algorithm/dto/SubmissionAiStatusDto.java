package kr.or.kosa.backend.algorithm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * AI 피드백 상태 응답 DTO
 *
 * Response DTO: 서비스에서 빌더로 생성, JSON 직렬화용
 * - @Builder: 서비스에서 객체 생성
 * - @AllArgsConstructor: Builder 내부에서 사용
 * - @Getter: Jackson이 JSON 직렬화
 */
@Getter
@Builder
@AllArgsConstructor
public class SubmissionAiStatusDto {
    private Long submissionId;

    private String aiFeedbackStatus;  // PENDING / COMPLETED / FAILED

    private BigDecimal aiScore;       // AI 점수

    private BigDecimal finalScore;    // Judge0 + AI 종합 점수

    private boolean hasAiFeedback;    // AI 피드백 생성 여부
}
