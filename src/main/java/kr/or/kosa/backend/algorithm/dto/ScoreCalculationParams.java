package kr.or.kosa.backend.algorithm.dto;

import kr.or.kosa.backend.algorithm.dto.enums.ProblemDifficulty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 점수 계산에 필요한 파라미터 DTO
 *
 * 내부 전달용 DTO: 서비스 간 불변 객체로 전달
 * - @Builder: 서비스에서 객체 생성
 * - @AllArgsConstructor: Builder 내부에서 사용
 * - @Getter: 값 읽기 전용
 */
@Getter
@Builder
@AllArgsConstructor
public class ScoreCalculationParams {

    /**
     * Judge0 채점 결과 (AC, WA, TLE, MLE, RE, CE)
     */
    private String judgeResult;

    /**
     * 통과한 테스트케이스 수
     */
    private Integer passedTestCount;

    /**
     * 전체 테스트케이스 수
     */
    private Integer totalTestCount;

    /**
     * AI 평가 점수 (0-100)
     */
    private Double aiScore;

    /**
     * 문제 풀이 소요 시간 (초)
     */
    private Integer solvingTimeSeconds;

    /**
     * 제한 시간 (초)
     */
    private Integer timeLimitSeconds;

    /**
     * 문제 난이도
     */
    private ProblemDifficulty difficulty;

    /**
     * Focus Score (Eye Tracking 점수)
     */
    private Double focusScore;

    /**
     * 추가 가산점 또는 감점 요소
     */
    private Double bonusScore;
}