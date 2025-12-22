package kr.or.kosa.backend.algorithm.dto;

import lombok.Getter;

/**
 * Phase 3: 다단계 유사도 임계값 설정
 *
 * 소스별로 다른 유사도 임계값을 적용하여 유연한 중복 검사 수행
 * - 수집 데이터 (BOJ 등): 높은 임계값 (반드시 회피)
 * - 생성 데이터: 중간 임계값 (AI 특성상 완화)
 * - 동일 테마: 낮은 임계값 (같은 테마면 허용)
 */
@Getter
public class SimilarityThresholds {

    private final double collectedThreshold;
    private final double generatedThreshold;
    private final double sameThemeThreshold;

    private SimilarityThresholds(double collectedThreshold, double generatedThreshold, double sameThemeThreshold) {
        this.collectedThreshold = collectedThreshold;
        this.generatedThreshold = generatedThreshold;
        this.sameThemeThreshold = sameThemeThreshold;
    }

    /**
     * 기본 임계값으로 생성
     */
    public static SimilarityThresholds defaults() {
        return new SimilarityThresholds(0.85, 0.75, 0.65);
    }

    /**
     * 사용자 정의 임계값으로 생성
     */
    public static SimilarityThresholds of(double collectedThreshold, double generatedThreshold, double sameThemeThreshold) {
        return new SimilarityThresholds(collectedThreshold, generatedThreshold, sameThemeThreshold);
    }
}
