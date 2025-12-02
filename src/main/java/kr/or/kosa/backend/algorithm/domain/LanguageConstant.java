package kr.or.kosa.backend.algorithm.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 언어별 채점 상수 도메인
 * LANGUAGE_CONSTANTS 테이블과 매핑
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LanguageConstant {
    
    /**
     * 언어명 (Primary Key)
     * 예: "Java 17", "Python 3", "C++17"
     */
    private String languageName;
    
    /**
     * 언어 유형
     * GENERAL: 일반 프로그래밍 언어 (알고리즘 문제용)
     * DB: 데이터베이스 언어 (SQL 문제용)
     */
    private LanguageType languageType;
    
    /**
     * 시간 제한 배수
     * 예: 2.0 → 기본 시간의 2배
     */
    private BigDecimal timeFactor;
    
    /**
     * 시간 제한 추가값 (ms)
     * 예: 1000 → 1초 추가
     */
    private Integer timeAddition;
    
    /**
     * 메모리 제한 배수
     * 예: 2.0 → 기본 메모리의 2배
     */
    private BigDecimal memoryFactor;
    
    /**
     * 메모리 제한 추가값 (MB)
     * 예: 16 → 16MB 추가
     */
    private Integer memoryAddition;
    
    /**
     * 실제 시간 제한 계산
     * 공식: (기본 시간 × timeFactor) + timeAddition
     * 
     * @param baseTimeLimit 문제의 기본 시간 제한 (ms)
     * @return 계산된 실제 시간 제한 (ms)
     */
    public int calculateRealTimeLimit(int baseTimeLimit) {
        double calculated = baseTimeLimit * timeFactor.doubleValue() + timeAddition;
        return (int) Math.round(calculated);
    }
    
    /**
     * 실제 메모리 제한 계산
     * 공식: (기본 메모리 × memoryFactor) + memoryAddition
     * 
     * @param baseMemoryLimit 문제의 기본 메모리 제한 (MB)
     * @return 계산된 실제 메모리 제한 (MB)
     */
    public int calculateRealMemoryLimit(int baseMemoryLimit) {
        double calculated = baseMemoryLimit * memoryFactor.doubleValue() + memoryAddition;
        return (int) Math.round(calculated);
    }
}
