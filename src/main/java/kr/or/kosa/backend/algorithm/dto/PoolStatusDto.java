package kr.or.kosa.backend.algorithm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * 문제 풀 상태 응답 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PoolStatusDto {

    /**
     * 현재 풀에 있는 총 문제 개수
     */
    private int totalCount;

    /**
     * 목표 문제 개수 (300조합 × 5개 = 1,500)
     */
    private int targetTotal;

    /**
     * 채우기 비율 (%)
     */
    private double fillRate;

    /**
     * 난이도별 문제 개수
     */
    private Map<String, Integer> byDifficulty;

    /**
     * 조합별 상세 현황 (선택적)
     * Map<difficulty, Map<topic, Map<theme, count>>>
     */
    private Map<String, Map<String, Map<String, Integer>>> details;
}
