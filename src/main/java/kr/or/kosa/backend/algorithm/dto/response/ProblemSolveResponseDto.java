package kr.or.kosa.backend.algorithm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

/**
 * 문제 풀이 시작 응답 DTO
 */
@Getter
@Builder
@AllArgsConstructor
public class ProblemSolveResponseDto {

    // 문제 기본 정보
    private Long problemId;
    private String title;
    private String description;
    private String difficulty;
    private Integer timeLimit;
    private Integer memoryLimit;

    // 문제 통계 정보
    private Integer totalAttempts;  // 총 제출 수
    private Integer successCount;   // 맞힌 사람 수

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

    @Getter
    @Builder
    @AllArgsConstructor
    public static class TestCaseDto {
        private String input;
        private String expectedOutput;
        private Boolean isSample;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class SubmissionSummaryDto {
        private Long submissionId;
        private String judgeResult;
        private BigDecimal finalScore;
        private LocalDateTime submittedAt;
    }

    /**
     * 언어 옵션 DTO
     * 변경사항 (2025-12-13): value (String) → languageId (Integer)
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class LanguageOption {
        private Integer languageId;   // LANGUAGES.LANGUAGE_ID (Judge0 API ID)
        private String languageName;  // 표시명 (예: "Python", "Java")
        private Integer timeLimit;    // 계산된 시간 제한 (ms)
        private Integer memoryLimit;  // 계산된 메모리 제한 (MB)
    }
}
