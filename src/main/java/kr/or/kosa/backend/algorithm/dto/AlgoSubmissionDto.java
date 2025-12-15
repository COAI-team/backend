package kr.or.kosa.backend.algorithm.dto;

import kr.or.kosa.backend.algorithm.dto.enums.JudgeResult;
import kr.or.kosa.backend.algorithm.dto.enums.AiFeedbackStatus;
import kr.or.kosa.backend.algorithm.dto.enums.AiFeedbackType;
import kr.or.kosa.backend.algorithm.dto.enums.SolveMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * 알고리즘 제출 DTO
 * 데이터베이스 테이블: ALGO_SUBMISSIONS
 *
 * 변경사항:
 * - focusScore, focusSessionId, eyetracked 제거 (모니터링이 점수에 미반영)
 * - solveMode 추가: BASIC(자유 풀이) vs FOCUS(집중 모드)
 * - monitoringSessionId 추가: FOCUS 모드에서 모니터링 세션 연결
 * - language (String) → languageId (Integer) 변경 (2025-12-13)
 * - githubCommitRequested, githubCommitStatus → githubCommitUrl 변경 (2025-12-13)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlgoSubmissionDto {

    // 기본 식별자
    private Long algosubmissionId;
    private Long algoProblemId;
    private Long userId;

    // 제출 코드 및 언어
    private String sourceCode;
    private Integer languageId;  // LANGUAGES.LANGUAGE_ID (Judge0 API ID)

    // Judge0 채점 결과
    private JudgeResult judgeResult;
    private Integer executionTime;
    private Integer memoryUsage;
    private Integer passedTestCount;
    private Integer totalTestCount;

    // AI 피드백 관련
    private String aiFeedback;
    private AiFeedbackStatus aiFeedbackStatus;
    private AiFeedbackType aiFeedbackType;

    // 풀이 모드 및 모니터링
    private SolveMode solveMode;
    private String monitoringSessionId;

    // 점수 관련 (focusScore 제거됨 - 모니터링은 점수에 미반영)
    private BigDecimal aiScore;
    private BigDecimal timeEfficiencyScore;
    private BigDecimal finalScore;
    private String scoreWeights;

    // 시간 추적
    private LocalDateTime startSolving;
    private LocalDateTime endSolving;
    private Integer solvingDurationSeconds;

    // GitHub 커밋 URL (NULL: 미커밋, 값: 커밋완료)
    private String githubCommitUrl;

    // 공유 설정
    private Boolean isShared;

    // 타임스탬프
    private LocalDateTime submittedAt;

    // 계산 메서드들

    /**
     * 테스트 통과율 계산 (0.0 ~ 100.0)
     */
    public Double getTestPassRate() {
        if (passedTestCount == null || totalTestCount == null || totalTestCount == 0) {
            return 0.0;
        }
        return (double) passedTestCount / totalTestCount * 100;
    }

    /**
     * 풀이 시간을 분 단위로 변환
     */
    public Integer getSolvingDurationMinutes() {
        if (solvingDurationSeconds == null) {
            return null;
        }
        return solvingDurationSeconds / 60;
    }
}
