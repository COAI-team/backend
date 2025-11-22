package kr.or.kosa.backend.algorithm.dto;

import kr.or.kosa.backend.algorithm.domain.ProgrammingLanguage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 제출 요약 정보 DTO (목록용)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionSummaryDto {

    private Long submissionId;
    private Long problemId;
    private String problemTitle;
    private String difficulty;
    private ProgrammingLanguage language;

    // 결과 요약
    private String judgeResult;
    private BigDecimal finalScore;
    private Integer passedTestCount;
    private Integer totalTestCount;
    private Double testPassRate;

    // 시간 정보
    private Integer solvingDurationMinutes;
    private LocalDateTime submittedAt;

    // 상태 정보
    private Boolean hasAiFeedback;
    private Boolean isShared;
    private String aiFeedbackStatus;
}

