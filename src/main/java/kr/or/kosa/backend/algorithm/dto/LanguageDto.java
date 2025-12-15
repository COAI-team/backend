package kr.or.kosa.backend.algorithm.dto;

import kr.or.kosa.backend.algorithm.dto.enums.LanguageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 언어 정보 DTO
 * 데이터베이스 테이블: LANGUAGES
 *
 * 변경사항 (2025-12-13):
 * - LANGUAGE_CONSTANTS → LANGUAGES 테이블 리팩토링
 * - languageId (INT, Judge0 API ID)를 Primary Key로 사용
 * - pistonLanguage 필드 추가 (Piston API 호환용)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LanguageDto {

    /**
     * 언어 ID (Primary Key)
     * Judge0 API의 language_id 값 사용
     * 예: Python=100, Java=91, C++=105
     */
    private Integer languageId;

    /**
     * 표시용 언어명
     * 예: "Python", "Java", "C++"
     */
    private String languageName;

    /**
     * Piston API용 언어명
     * 예: "python", "java", "c++"
     */
    private String pistonLanguage;

    /**
     * 언어 유형 (GENERAL: 일반 프로그래밍, DB: 데이터베이스)
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
     * baseTimeLimit * timeFactor + timeAddition
     *
     * @param baseTimeLimit 기본 시간 제한 (ms)
     * @return 계산된 실제 시간 제한 (ms)
     */
    public int calculateRealTimeLimit(int baseTimeLimit) {
        if (timeFactor == null) {
            return baseTimeLimit + (timeAddition != null ? timeAddition : 0);
        }
        double calculated = baseTimeLimit * timeFactor.doubleValue() +
                           (timeAddition != null ? timeAddition : 0);
        return (int) Math.round(calculated);
    }

    /**
     * 실제 메모리 제한 계산
     * baseMemoryLimit * memoryFactor + memoryAddition
     *
     * @param baseMemoryLimit 기본 메모리 제한 (MB)
     * @return 계산된 실제 메모리 제한 (MB)
     */
    public int calculateRealMemoryLimit(int baseMemoryLimit) {
        if (memoryFactor == null) {
            return baseMemoryLimit + (memoryAddition != null ? memoryAddition : 0);
        }
        double calculated = baseMemoryLimit * memoryFactor.doubleValue() +
                           (memoryAddition != null ? memoryAddition : 0);
        return (int) Math.round(calculated);
    }
}
