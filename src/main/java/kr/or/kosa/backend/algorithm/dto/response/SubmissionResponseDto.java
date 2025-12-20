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
    private String inputFormat;        // 입력 형식 설명
    private String outputFormat;       // 출력 형식 설명
    private String constraints;        // 제한 사항
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

    // XP 관련 (2025-12-17 추가)
    private Integer earnedXp;       // 이번 제출로 획득한 XP (AC일 때만)
    private Boolean isFirstSolve;   // 첫 정답 여부 (1.5배 보너스 적용)

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
        private Integer sleepingCount;          // 졸음 감지 횟수
        private Integer multipleFacesCount;     // 다중 인물 감지 횟수
        private Integer maskDetectedCount;      // 깜빡임 없음 (liveness) 횟수
        private Integer totalViolations;        // 총 위반 횟수
        private Integer warningShownCount;      // 경고 표시 횟수
        private Boolean autoSubmitted;          // 자동 제출 여부
        private String sessionStatus;           // 세션 상태 (ACTIVE, COMPLETED, TIMEOUT)

        // 집중도 점수 통계
        private Double focusAvgScore;           // 평균 집중도 점수 (-100 ~ +100)
        private Double focusFinalScore;         // 최종 집중도 점수
        private Double focusFocusedPercentage;  // 집중 시간 비율 (%)
        private Double focusHighFocusPercentage;// 고집중 시간 비율 (%)
        private Long focusTotalTime;            // 총 측정 시간 (ms)
        private Long focusFocusedTime;          // 집중 상태 시간 (ms)
    }
}
