package kr.or.kosa.backend.algorithm.dto;

import lombok.Builder;
import lombok.Data; /**
 * 점수 계산 파라미터 DTO
 */
@Data
@Builder
public class ScoreCalculationParams { 
    private String judgeResult;
    private Integer passedTestCount;
    private Integer totalTestCount;
    private Double aiScore;
    private Integer solvingTimeSeconds;
    private Integer timeLimitSeconds;
}
