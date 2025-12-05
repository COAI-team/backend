package kr.or.kosa.backend.algorithm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

/**
 * 제출 결과 응답 DTO
 *
 * 변경사항:
 * - focusScore 제거 (모니터링은 점수에 미반영)
 * - solveMode, monitoringSessionId 추가
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionResponseDto {

    // 제출 기본 정보
    private Long submissionId;
    private Long problemId;
    private String problemTitle;
    private String language; // DB 언어명 (예: "Python 3", "Java 17")
    private String sourceCode;

    // 채점 결과
    private String judgeResult;
    private String judgeStatus; // PENDING, JUDGING, COMPLETED
    private Integer executionTime;
    private Integer memoryUsage;
    private Integer passedTestCount;
    private Integer totalTestCount;
    private Double testPassRate;

    // 각 테스트케이스 결과
    private List<TestCaseResultDto> testCaseResults;

    // AI 피드백
    private String aiFeedback;
    private String aiFeedbackStatus;
    private BigDecimal aiScore;

    // 풀이 모드 및 모니터링
    private String solveMode; // BASIC, FOCUS
    private String monitoringSessionId;

    // 점수 정보 (focusScore 제거됨 - 모니터링은 점수에 미반영)
    private BigDecimal timeEfficiencyScore;
    private BigDecimal finalScore;
    private ScoreBreakdownDto scoreBreakdown;

    // 시간 정보
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer solvingDurationSeconds;
    private Integer solvingDurationMinutes;

    // 공유 설정
    private Boolean isShared;

    // 제출 시각
    private LocalDateTime submittedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestCaseResultDto {
        private Integer testCaseNumber;
        private String input;
        private String expectedOutput;
        private String actualOutput;
        private String result; // PASS, FAIL, ERROR
        private Integer executionTime;
        private String errorMessage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreBreakdownDto {
        private BigDecimal judgeScore;      // 채점 점수 (40%)
        private BigDecimal aiScore;         // AI 품질 점수 (30%)
        private BigDecimal timeScore;       // 시간 효율성 (30%)
        // focusScore 제거됨 - 모니터링은 점수에 미반영
        private String scoreWeights;        // 가중치 설명
    }
}
