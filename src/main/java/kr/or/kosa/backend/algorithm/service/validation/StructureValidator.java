package kr.or.kosa.backend.algorithm.service.validation;

import kr.or.kosa.backend.algorithm.dto.AlgoProblemDto;
import kr.or.kosa.backend.algorithm.dto.AlgoTestcaseDto;
import kr.or.kosa.backend.algorithm.dto.ValidationResultDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Phase 4-1: 구조 검증기
 * LLM이 생성한 문제의 구조적 완결성을 검증
 */
@Slf4j
@Component
public class StructureValidator {

    private static final String VALIDATOR_NAME = "StructureValidator";

    @Value("${algorithm.validation.min-testcases:3}")
    private int minTestCases;

    @Value("${algorithm.validation.min-description-length:50}")
    private int minDescriptionLength;

    @Value("${algorithm.validation.min-title-length:5}")
    private int minTitleLength;

    /**
     * 문제 구조 검증
     *
     * @param problem   생성된 문제 DTO
     * @param testCases 생성된 테스트케이스 목록
     * @param optimalCode 최적 풀이 코드 (nullable)
     * @param naiveCode   비효율 풀이 코드 (nullable)
     * @return 검증 결과
     */
    public ValidationResultDto validate(
            AlgoProblemDto problem,
            List<AlgoTestcaseDto> testCases,
            String optimalCode,
            String naiveCode) {

        log.info("구조 검증 시작 - 문제: {}", problem != null ? problem.getAlgoProblemTitle() : "null");

        ValidationResultDto result = ValidationResultDto.builder()
                .passed(true)
                .validatorName(VALIDATOR_NAME)
                .build();

        // 1. 문제 기본 정보 검증
        validateProblemBasics(problem, result);

        // 2. 테스트케이스 검증
        validateTestCases(testCases, result);

        // 3. 검증용 코드 검증
        validateValidationCode(optimalCode, naiveCode, result);

        // 4. 제약 조건 및 형식 검증
        validateConstraints(problem, result);

        log.info("구조 검증 완료 - 결과: {}", result.getSummary());
        return result;
    }

    /**
     * 문제 기본 정보 검증
     */
    private void validateProblemBasics(AlgoProblemDto problem, ValidationResultDto result) {
        if (problem == null) {
            result.addError("문제 정보가 없습니다");
            return;
        }

        // 제목 검증
        if (problem.getAlgoProblemTitle() == null || problem.getAlgoProblemTitle().isBlank()) {
            result.addError("문제 제목이 없습니다");
        } else if (problem.getAlgoProblemTitle().length() < minTitleLength) {
            result.addError(String.format("문제 제목이 너무 짧습니다 (최소 %d자)", minTitleLength));
        }

        // 설명 검증
        if (problem.getAlgoProblemDescription() == null || problem.getAlgoProblemDescription().isBlank()) {
            result.addError("문제 설명이 없습니다");
        } else if (problem.getAlgoProblemDescription().length() < minDescriptionLength) {
            result.addError(String.format("문제 설명이 너무 짧습니다 (최소 %d자)", minDescriptionLength));
        }

        // 난이도 검증
        if (problem.getAlgoProblemDifficulty() == null) {
            result.addError("난이도 정보가 없습니다");
        }

        // 시간/메모리 제한 검증
        if (problem.getTimelimit() == null || problem.getTimelimit() <= 0) {
            result.addWarning("시간 제한이 설정되지 않았습니다. 기본값이 적용됩니다.");
        }
        if (problem.getMemorylimit() == null || problem.getMemorylimit() <= 0) {
            result.addWarning("메모리 제한이 설정되지 않았습니다. 기본값이 적용됩니다.");
        }
    }

    /**
     * 테스트케이스 검증
     */
    private void validateTestCases(List<AlgoTestcaseDto> testCases, ValidationResultDto result) {
        if (testCases == null || testCases.isEmpty()) {
            result.addError("테스트케이스가 없습니다");
            return;
        }

        if (testCases.size() < minTestCases) {
            result.addError(String.format("테스트케이스가 부족합니다 (최소 %d개, 현재 %d개)",
                    minTestCases, testCases.size()));
        }

        // 샘플 테스트케이스 존재 여부 확인
        long sampleCount = testCases.stream()
                .filter(tc -> Boolean.TRUE.equals(tc.getIsSample()))
                .count();
        if (sampleCount == 0) {
            result.addWarning("샘플 테스트케이스가 없습니다. 사용자에게 예시를 보여줄 수 없습니다.");
        }

        // 각 테스트케이스 검증
        for (int i = 0; i < testCases.size(); i++) {
            AlgoTestcaseDto tc = testCases.get(i);
            String prefix = String.format("테스트케이스 %d", i + 1);

            if (tc.getInputData() == null) {
                result.addError(prefix + ": 입력 데이터가 없습니다");
            }
            // 빈 문자열("")은 유효한 출력임 (예: 입력이 0인 경우 출력 없음)
            // null만 에러로 처리
            if (tc.getExpectedOutput() == null) {
                result.addError(prefix + ": 예상 출력이 없습니다");
            }
        }

        result.addMetadata("testCaseCount", testCases.size());
        result.addMetadata("sampleCount", sampleCount);
    }

    /**
     * 검증용 코드 검증
     */
    private void validateValidationCode(String optimalCode, String naiveCode, ValidationResultDto result) {
        boolean hasOptimal = optimalCode != null && !optimalCode.isBlank();
        boolean hasNaive = naiveCode != null && !naiveCode.isBlank();

        if (!hasOptimal) {
            result.addWarning("최적 풀이 코드가 없습니다. 코드 실행 검증을 건너뜁니다.");
        } else {
            // 기본적인 코드 구조 검증
            if (!containsMainLogic(optimalCode)) {
                result.addWarning("최적 풀이 코드에 메인 로직이 없을 수 있습니다");
            }
        }

        if (!hasNaive) {
            result.addWarning("비효율 풀이 코드가 없습니다. 시간 비율 검증을 건너뜁니다.");
        }

        result.addMetadata("hasOptimalCode", hasOptimal);
        result.addMetadata("hasNaiveCode", hasNaive);
    }

    /**
     * 제약 조건 및 형식 검증
     */
    private void validateConstraints(AlgoProblemDto problem, ValidationResultDto result) {
        if (problem == null) {
            return;
        }

        // 제약 조건 존재 여부
        if (problem.getConstraints() == null || problem.getConstraints().isBlank()) {
            result.addWarning("제약 조건이 명시되지 않았습니다");
        }

        // 입출력 형식 존재 여부
        if (problem.getInputFormat() == null || problem.getInputFormat().isBlank()) {
            result.addWarning("입력 형식이 명시되지 않았습니다");
        }
        if (problem.getOutputFormat() == null || problem.getOutputFormat().isBlank()) {
            result.addWarning("출력 형식이 명시되지 않았습니다");
        }
    }

    /**
     * 코드에 메인 로직이 포함되어 있는지 간단히 확인
     */
    private boolean containsMainLogic(String code) {
        if (code == null) {
            return false;
        }

        String lowerCode = code.toLowerCase();

        // Python 스타일
        if (lowerCode.contains("input(") || lowerCode.contains("sys.stdin")) {
            return true;
        }

        // Java 스타일
        if (lowerCode.contains("scanner") || lowerCode.contains("bufferedreader")) {
            return true;
        }

        // C/C++ 스타일
        if (lowerCode.contains("scanf") || lowerCode.contains("cin")) {
            return true;
        }

        // 일반적인 출력
        return lowerCode.contains("print") || lowerCode.contains("cout") ||
               lowerCode.contains("system.out") || lowerCode.contains("printf");
    }
}
