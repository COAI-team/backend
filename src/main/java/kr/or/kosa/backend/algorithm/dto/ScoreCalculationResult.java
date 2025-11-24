package kr.or.kosa.backend.algorithm.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map; /**
 * 점수 계산 결과 DTO
 */
@Data
@Builder
public class ScoreCalculationResult {
    private Double finalScore;
    private Double judgeScore;
    private Double aiScore;
    private Double timeEfficiencyScore;
    private Map<String, Object> scoreWeights;
    private String scoreGrade;
}
