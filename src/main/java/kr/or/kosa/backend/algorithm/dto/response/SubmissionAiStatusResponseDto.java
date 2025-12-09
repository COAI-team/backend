package kr.or.kosa.backend.algorithm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * AI 피드백 상태 응답 DTO
 */
@Getter
@Builder
@AllArgsConstructor
public class SubmissionAiStatusResponseDto {
    private Long submissionId;

    private String aiFeedbackStatus;  // PENDING / COMPLETED / FAILED

    private BigDecimal aiScore;       // AI 점수

    private BigDecimal finalScore;    // Judge0 + AI 종합 점수

    private boolean hasAiFeedback;    // AI 피드백 생성 여부
}
