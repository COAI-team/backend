package kr.or.kosa.backend.algorithm.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionAiStatusDto {
    private Long submissionId;

    private String aiFeedbackStatus;  // PENDING / COMPLETED / FAILED

    private BigDecimal aiScore;       // AI 점수

    private BigDecimal finalScore;    // Judge0 + AI 종합 점수

    private boolean hasAiFeedback;    // AI 피드백 생성 여부
}
