package kr.or.kosa.backend.algorithm.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * 알고리즘 제출 엔티티 (ALG-07 관련)
 * AUTO_INCREMENT로 1000번부터 시작하도록 설정됨
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlgoSubmission {

    // 기본 식별자
    private Long algosubmissionId;          // AUTO_INCREMENT (1000부터 시작)
    private Long algoProblemId;             // 문제 ID (FK)
    private Long userId;                    // 제출자 ID (FK)

    // 제출 코드 및 언어
    private String sourceCode;              // 제출한 소스코드
    private String language;                // 프로그래밍 언어 (DB 언어명: "Python 3", "Java 17", "C++17" 등)

    // Judge0 채점 결과
    private JudgeResult judgeResult;        // AC, WA, TLE, MLE, RE, CE, PENDING
    private Integer executionTime;          // 실행 시간(ms)
    private Integer memoryUsage;            // 메모리 사용량(MB)
    private Integer passedTestCount;        // 통과한 테스트케이스 수
    private Integer totalTestCount;         // 전체 테스트케이스 수

    // AI 피드백 관련
    private String aiFeedback;              // AI 코드 리뷰 결과
    private AiFeedbackStatus aiFeedbackStatus; // PENDING, COMPLETED, FAILED
    private AiFeedbackType aiFeedbackType;  // QUALITY, PERFORMANCE, STYLE, COMPREHENSIVE

    // 점수 관련 (ALG-08)
    private BigDecimal focusScore;          // 시선추적 집중도 점수 (0-100)
    private BigDecimal aiScore;             // AI 코드 품질 점수 (0-100)
    private BigDecimal timeEfficiencyScore; // 시간 효율성 점수 (0-100)
    private BigDecimal finalScore;          // 최종 종합 점수 (0-100)
    private String scoreWeights;            // 점수 가중치 정보 (JSON)

    // 시간 추적
    private LocalDateTime startSolving;     // 문제 풀이 시작 시각
    private LocalDateTime endSolving;       // 문제 풀이 종료 시각
    private Integer solvingDurationSeconds; // 총 풀이 소요 시간(초)

    // Eye Tracking 관련
    private String focusSessionId;          // 연결된 집중 추적 세션 ID
    private Boolean eyetracked;             // 시선 추적 사용 여부

    // GitHub 연동 관련
    private Boolean githubCommitRequested;  // GitHub 자동 커밋 요청 여부
    private GithubCommitStatus githubCommitStatus; // NONE, PENDING, COMPLETED, FAILED

    // 공유 설정 (ALG-09)
    private Boolean isShared;               // 게시글로 공유 여부

    // 타임스탬프
    private LocalDateTime submittedAt;      // 제출 시각

    /**
     * 채점 결과 Enum
     */
    public enum JudgeResult {
        AC("Accepted"),
        WA("Wrong Answer"),
        TLE("Time Limit Exceeded"),
        MLE("Memory Limit Exceeded"),
        RE("Runtime Error"),
        CE("Compile Error"),
        PENDING("Pending");

        private final String description;

        JudgeResult(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * AI 피드백 상태 Enum
     */
    public enum AiFeedbackStatus {
        PENDING, COMPLETED, FAILED
    }

    /**
     * AI 피드백 타입 Enum
     */
    public enum AiFeedbackType {
        QUALITY,        // 코드 품질 위주
        PERFORMANCE,    // 성능 최적화 위주
        STYLE,          // 코딩 스타일 위주
        COMPREHENSIVE   // 종합적 분석
    }

    /**
     * GitHub 커밋 상태 Enum
     */
    public enum GithubCommitStatus {
        NONE, PENDING, COMPLETED, FAILED
    }

    // 편의 메서드들

    /**
     * 채점이 완료되었는지 확인
     */
    public boolean isJudgingCompleted() {
        return judgeResult != null && judgeResult != JudgeResult.PENDING;
    }

    /**
     * 정답 여부 확인
     */
    public boolean isAccepted() {
        return JudgeResult.AC.equals(judgeResult);
    }

    /**
     * 풀이 시간 계산 (분)
     */
    public Integer getSolvingDurationMinutes() {
        return solvingDurationSeconds != null ? solvingDurationSeconds / 60 : null;
    }

    /**
     * 테스트케이스 통과율 계산
     */
    public Double getTestPassRate() {
        if (totalTestCount == null || totalTestCount == 0) {
            return 0.0;
        }
        return (double) (passedTestCount != null ? passedTestCount : 0) / totalTestCount * 100;
    }

    /**
     * AI 피드백 준비 상태 확인
     */
    public boolean isReadyForAiFeedback() {
        return isJudgingCompleted() && sourceCode != null && !sourceCode.trim().isEmpty();
    }
}