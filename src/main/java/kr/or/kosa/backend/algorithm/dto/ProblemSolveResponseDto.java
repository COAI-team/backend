package kr.or.kosa.backend.algorithm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

// ====== ProblemSolveResponseDto.java ======
/**
 * 문제 풀이 시작 응답 DTO (ALG-04)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemSolveResponseDto {

    // 문제 기본 정보
    private Long problemId;
    private String title;
    private String description;
    private String difficulty;
    private Integer timeLimit;
    private Integer memoryLimit;

    // 샘플 테스트케이스
    private List<TestCaseDto> sampleTestCases;

    // 세션 정보
    private LocalDateTime sessionStartTime;
    private String sessionId; // Eye Tracking용 세션 ID (나중에 사용)

    // 이전 제출 정보 (있다면)
    private SubmissionSummaryDto previousSubmission;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestCaseDto {
        private String input;
        private String expectedOutput;
        private Boolean isSample;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmissionSummaryDto {
        private Long submissionId;
        private String judgeResult;
        private BigDecimal finalScore;
        private LocalDateTime submittedAt;
    }
}
