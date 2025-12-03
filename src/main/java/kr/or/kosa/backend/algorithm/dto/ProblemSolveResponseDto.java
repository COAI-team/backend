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

    // 추가 정보 (SQL 문제 및 언어별 제한)
    private String problemType; // ALGORITHM, SQL
    private String initScript; // SQL 문제용 초기화 스크립트
    private List<LanguageOption> availableLanguages; // 언어별 제한 정보

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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LanguageOption {
        private String languageName; // 표시명 (예: "Java 17")
        private String value; // 제출용 값 (예: "JAVA")
        private Integer timeLimit; // 계산된 시간 제한
        private Integer memoryLimit; // 계산된 메모리 제한
    }
}
