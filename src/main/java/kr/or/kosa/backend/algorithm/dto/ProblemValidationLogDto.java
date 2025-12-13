package kr.or.kosa.backend.algorithm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 문제 생성 검증 로그 DTO
 * LLM이 생성한 문제의 품질 검증 결과를 저장
 * 개발자 검토용 (사용자에게 노출되지 않음)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProblemValidationLogDto {

    /**
     * 검증 로그 고유 식별자
     */
    private Long validationId;

    /**
     * 연관된 문제 ID
     */
    private Long algoProblemId;

    /**
     * LLM이 생성한 최적 정답 코드
     */
    private String optimalCode;

    /**
     * LLM이 생성한 비효율적 코드 (시간 복잡도 비교용)
     */
    private String naiveCode;

    /**
     * 예상 시간 복잡도 (예: O(n log n))
     */
    private String expectedTimeComplexity;

    /**
     * 최적 코드 실행 결과 (PASS, FAIL)
     */
    private String optimalCodeResult;

    /**
     * 비효율 코드 실행 결과
     */
    private String naiveCodeResult;

    /**
     * 최적 코드 실행 시간 (ms)
     */
    private Integer optimalExecutionTime;

    /**
     * 비효율 코드 실행 시간 (ms)
     */
    private Integer naiveExecutionTime;

    /**
     * 시간 비율 (naive / optimal)
     */
    private Double timeRatio;

    /**
     * 시간 비율 검증 통과 여부
     */
    private Boolean timeRatioValid;

    /**
     * 기존 문제와의 유사도 점수 (0-100)
     */
    private Double similarityScore;

    /**
     * 유사도 검증 통과 여부
     */
    private Boolean similarityValid;

    /**
     * 검증 상태 (PENDING, PASSED, FAILED, CORRECTED)
     */
    private String validationStatus;

    /**
     * Self-Correction 시도 횟수
     */
    private Integer correctionAttempts;

    /**
     * 검증 실패 원인 (JSON 형식)
     */
    private String failureReasons;

    /**
     * 검증 생성 일시
     */
    private LocalDateTime createdAt;

    /**
     * 검증 완료 일시
     */
    private LocalDateTime completedAt;
}
