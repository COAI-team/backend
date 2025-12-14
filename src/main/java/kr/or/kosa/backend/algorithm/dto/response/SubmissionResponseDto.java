package kr.or.kosa.backend.algorithm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

/**
 * 제출 결과 응답 DTO
 *
 * 변경사항 (2025-12-13):
 * - language (String) → languageId (INT) + languageName (String)
 * - languageId: LANGUAGES.LANGUAGE_ID (Judge0 API ID)
 * - languageName: 표시용 언어 이름 (예: "Python 3", "Java 17")
 */
@Getter
@Builder
@AllArgsConstructor
public class SubmissionResponseDto {

    // 제출 기본 정보
    private Long submissionId;
    private Long problemId;
    private String problemTitle;
    private String problemDescription; // 문제 설명 (제출 결과 페이지에서 문제 확인용)
    private String difficulty;         // 난이도 (BRONZE, SILVER, GOLD, PLATINUM)
    private Integer timeLimit;         // 시간 제한 (ms)
    private Integer memoryLimit;       // 메모리 제한 (MB)
    private Integer languageId;        // 언어 ID (LANGUAGES.LANGUAGE_ID, Judge0 API ID)
    private String languageName;       // 표시용 언어명 (예: "Python 3", "Java 17")
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
    private MonitoringStatsDto monitoringStats; // 집중 모드 모니터링 통계

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

    // GitHub 커밋 URL (NULL: 미커밋, 값: 커밋완료)
    private String githubCommitUrl;

    // 제출 시각
    private LocalDateTime submittedAt;

    @Getter
    @Builder
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

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ScoreBreakdownDto {
        private BigDecimal judgeScore;      // 채점 점수 (40%)
        private BigDecimal aiScore;         // AI 품질 점수 (30%)
        private BigDecimal timeScore;       // 시간 효율성 (30%)
        // focusScore 제거됨 - 모니터링은 점수에 미반영
        private String scoreWeights;        // 가중치 설명
    }

    /**
     * 집중 모드 모니터링 통계 DTO
     * 제출 결과 페이지에서 위반 현황 표시용
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class MonitoringStatsDto {
        private Integer fullscreenExitCount;    // 전체화면 이탈 횟수
        private Integer tabSwitchCount;         // 탭 전환 횟수
        private Integer mouseLeaveCount;        // 마우스 이탈 횟수
        private Integer noFaceCount;            // 얼굴 미검출 횟수
        private Integer gazeAwayCount;          // 시선 이탈 횟수
        private Integer totalViolations;        // 총 위반 횟수
        private Integer warningShownCount;      // 경고 표시 횟수
        private Boolean autoSubmitted;          // 자동 제출 여부
        private String sessionStatus;           // 세션 상태 (ACTIVE, COMPLETED, TIMEOUT)
    }
}
