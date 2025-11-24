package kr.or.kosa.backend.algorithm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 점수 계산 결과 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreCalculationResult {

    /**
     * 최종 종합 점수 (0-100)
     */
    private Double finalScore;

    /**
     * Judge 점수 (0-100)
     */
    private Double judgeScore;

    /**
     * AI 코드 품질 점수 (0-100)
     */
    private Double aiScore;

    /**
     * 시간 효율성 점수 (0-100)
     */
    private Double timeEfficiencyScore;

    /**
     * Focus 점수 (0-100)
     */
    private Double focusScore;

    /**
     * 점수 가중치 상세 정보
     */
    private Map<String, Object> scoreWeights;

    /**
     * 점수 등급 (A+, A, B+, B, C+, C, D+, D, F)
     */
    private String scoreGrade;

    /**
     * 점수 계산 메타 정보
     */
    private String calculationMethod;

    /**
     * 추가 점수 설명
     */
    private String scoreExplanation;
}