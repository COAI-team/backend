package kr.or.kosa.backend.algorithm.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * LLM 응답 DTO
 * 응답 내용과 토큰 사용량 정보를 포함
 */
@Getter
@Builder
public class LLMResponseDto {

    /**
     * LLM 응답 텍스트
     */
    private final String content;

    /**
     * 입력 토큰 수 (프롬프트)
     */
    private final Integer inputTokens;

    /**
     * 출력 토큰 수 (응답)
     */
    private final Integer outputTokens;

    /**
     * 총 토큰 수
     */
    private final Integer totalTokens;

    /**
     * 응답 생성 시간 (밀리초)
     */
    private final Long responseTimeMs;

    /**
     * 사용된 모델명
     */
    private final String model;

    /**
     * 토큰 정보가 있는지 확인
     */
    public boolean hasTokenInfo() {
        return inputTokens != null && outputTokens != null;
    }

    /**
     * 예상 비용 계산 (GPT-4o 기준, USD)
     * - Input: $2.50 / 1M tokens
     * - Output: $10.00 / 1M tokens
     */
    public double estimateCostUsd() {
        if (!hasTokenInfo()) {
            return 0.0;
        }
        double inputCost = (inputTokens / 1_000_000.0) * 2.50;
        double outputCost = (outputTokens / 1_000_000.0) * 10.00;
        return inputCost + outputCost;
    }
}
