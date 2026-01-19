package kr.or.kosa.backend.algorithm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * 점수 계산 결과 DTO
 *
 * 내부 전달용 DTO: 서비스 간 불변 객체로 전달
 * - @Builder: 서비스에서 객체 생성
 * - @AllArgsConstructor: Builder 내부에서 사용
 * - @Getter: 값 읽기 전용
 */
@Getter
@Builder
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