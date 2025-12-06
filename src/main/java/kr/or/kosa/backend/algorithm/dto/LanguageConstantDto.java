package kr.or.kosa.backend.algorithm.dto;

import kr.or.kosa.backend.algorithm.dto.enums.LanguageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 언어별 채점 상수 DTO
 * 데이터베이스 테이블: LANGUAGE_CONSTANTS
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LanguageConstantDto {

    /**
     * 언어명 (Primary Key)
     */
    private String languageName;

    /**
     * 언어 유형
     */
    private LanguageType languageType;

    /**
     * 시간 제한 배수
     */
    private BigDecimal timeFactor;

    /**
     * 시간 제한 추가값 (ms)
     */
    private Integer timeAddition;

    /**
     * 메모리 제한 배수
     */
    private BigDecimal memoryFactor;

    /**
     * 메모리 제한 추가값 (MB)
     */
    private Integer memoryAddition;

    /**
     * 실제 시간 제한 계산
     */
    public int calculateRealTimeLimit(int baseTimeLimit) {
        double calculated = baseTimeLimit * timeFactor.doubleValue() + timeAddition;
        return (int) Math.round(calculated);
    }

    /**
     * 실제 메모리 제한 계산
     */
    public int calculateRealMemoryLimit(int baseMemoryLimit) {
        double calculated = baseMemoryLimit * memoryFactor.doubleValue() + memoryAddition;
        return (int) Math.round(calculated);
    }
}
