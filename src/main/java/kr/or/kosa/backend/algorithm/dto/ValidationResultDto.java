package kr.or.kosa.backend.algorithm.dto;

import lombok.Getter;
import lombok.Builder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 검증 결과 DTO
 * 모든 검증기가 공통으로 사용
 */
@Getter
@Builder
public class ValidationResultDto {

    /** 검증 통과 여부 */
    private boolean passed;

    /** 검증 단계 이름 */
    private final String validatorName;

    /** 오류 메시지 목록 */
    @Builder.Default
    private final List<String> errors = new ArrayList<>();

    /** 경고 메시지 목록 */
    @Builder.Default
    private final List<String> warnings = new ArrayList<>();

    /** 추가 메타데이터 (실행 시간, 점수 등) */
    @Builder.Default
    private final Map<String, Object> metadata = new HashMap<>();

    /**
     * 성공 결과 생성
     */
    public static ValidationResultDto success(String validatorName) {
        return ValidationResultDto.builder()
                .passed(true)
                .validatorName(validatorName)
                .build();
    }

    /**
     * 실패 결과 생성
     */
    public static ValidationResultDto failure(String validatorName, String error) {
        ValidationResultDto result = ValidationResultDto.builder()
                .passed(false)
                .validatorName(validatorName)
                .build();
        result.errors.add(error);
        return result;
    }

    /**
     * 실패 결과 생성 (다중 오류)
     */
    public static ValidationResultDto failure(String validatorName, List<String> errorList) {
        return ValidationResultDto.builder()
                .passed(false)
                .validatorName(validatorName)
                .errors(new ArrayList<>(errorList))
                .build();
    }

    /**
     * 오류 추가
     */
    public void addError(String error) {
        this.errors.add(error);
        this.passed = false;
    }

    /**
     * 경고 추가
     */
    public void addWarning(String warning) {
        this.warnings.add(warning);
    }

    /**
     * 메타데이터 추가
     */
    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }

    /**
     * 결과 요약 문자열
     */
    public String getSummary() {
        if (passed) {
            return String.format("[%s] PASSED", validatorName);
        } else {
            return String.format("[%s] FAILED - %d errors: %s",
                    validatorName, errors.size(), String.join(", ", errors));
        }
    }
}
