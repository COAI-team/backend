package kr.or.kosa.backend.algorithm.service.validation;

import kr.or.kosa.backend.algorithm.dto.AlgoTestcaseDto;
import kr.or.kosa.backend.algorithm.dto.ValidationResultDto;
import kr.or.kosa.backend.algorithm.dto.response.TestRunResponseDto;
import kr.or.kosa.backend.algorithm.service.CodeExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Phase 4-2: 코드 실행 검증기
 * Judge0 API를 사용하여 생성된 코드가 테스트케이스를 통과하는지 검증
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeExecutionValidator {

    private static final String VALIDATOR_NAME = "CodeExecutionValidator";

    private final CodeExecutorService codeExecutorService;  // Judge0 또는 Piston 선택

    @Value("${algorithm.validation.execution-timeout:30}")
    private int executionTimeoutSeconds;

    @Value("${algorithm.validation.default-time-limit:2000}")
    private int defaultTimeLimit;

    @Value("${algorithm.validation.default-memory-limit:256}")
    private int defaultMemoryLimit;

    /**
     * 최적 풀이 코드 실행 검증
     *
     * @param optimalCode 최적 풀이 코드
     * @param language    프로그래밍 언어 (DB 언어명)
     * @param testCases   테스트케이스 목록
     * @param timeLimit   시간 제한 (ms, nullable)
     * @param memoryLimit 메모리 제한 (MB, nullable)
     * @return 검증 결과
     */
    public ValidationResultDto validate(
            String optimalCode,
            String language,
            List<AlgoTestcaseDto> testCases,
            Integer timeLimit,
            Integer memoryLimit) {

        log.info("코드 실행 검증 시작 - language: {}, testCases: {}", language, testCases != null ? testCases.size() : 0);

        ValidationResultDto result = ValidationResultDto.builder()
                .passed(true)
                .validatorName(VALIDATOR_NAME)
                .build();

        // 1. 입력 검증
        if (!validateInputs(optimalCode, language, testCases, result)) {
            return result;
        }

        // 2. 시간/메모리 제한 설정
        int effectiveTimeLimit = timeLimit != null && timeLimit > 0 ? timeLimit : defaultTimeLimit;
        int effectiveMemoryLimit = memoryLimit != null && memoryLimit > 0 ? memoryLimit : defaultMemoryLimit;

        // 3. Judge0로 코드 실행
        try {
            TestRunResponseDto judgeResult = codeExecutorService.judgeCode(
                    optimalCode,
                    language,
                    testCases,
                    effectiveTimeLimit,
                    effectiveMemoryLimit
            ).get(executionTimeoutSeconds, TimeUnit.SECONDS);

            // 4. 결과 분석
            analyzeJudgeResult(judgeResult, result);

        } catch (Exception e) {
            log.error("코드 실행 검증 중 오류 발생", e);
            result.addError("코드 실행 중 오류 발생: " + e.getMessage());
        }

        log.info("코드 실행 검증 완료 - 결과: {}", result.getSummary());
        return result;
    }

    /**
     * 입력값 유효성 검증
     */
    private boolean validateInputs(
            String optimalCode,
            String language,
            List<AlgoTestcaseDto> testCases,
            ValidationResultDto result) {

        if (optimalCode == null || optimalCode.isBlank()) {
            result.addWarning("최적 풀이 코드가 없어 코드 실행 검증을 건너뜁니다");
            result.addMetadata("skipped", true);
            result.addMetadata("skipReason", "NO_OPTIMAL_CODE");
            return false;
        }

        if (language == null || language.isBlank()) {
            result.addError("프로그래밍 언어가 지정되지 않았습니다");
            return false;
        }

        if (testCases == null || testCases.isEmpty()) {
            result.addError("테스트케이스가 없습니다");
            return false;
        }

        return true;
    }

    /**
     * Judge0 실행 결과 분석
     */
    private void analyzeJudgeResult(TestRunResponseDto judgeResult, ValidationResultDto result) {
        // 메타데이터 추가
        result.addMetadata("overallResult", judgeResult.getOverallResult());
        result.addMetadata("passedTestCount", judgeResult.getPassedCount());
        result.addMetadata("totalTestCount", judgeResult.getTotalCount());
        result.addMetadata("testPassRate", judgeResult.getTestPassRate());
        result.addMetadata("maxExecutionTime", judgeResult.getMaxExecutionTime());
        result.addMetadata("maxMemoryUsage", judgeResult.getMaxMemoryUsage());

        // 결과 판정
        String overallResult = judgeResult.getOverallResult();

        if ("AC".equals(overallResult)) {
            log.info("모든 테스트케이스 통과 - 통과율: {}%", judgeResult.getTestPassRate());
            return;
        }

        // 실패 케이스 분석
        switch (overallResult) {
            case "CE":
                result.addError("컴파일 에러가 발생했습니다");
                addFailedTestCaseDetails(judgeResult, result);
                break;
            case "RE":
                result.addError("런타임 에러가 발생했습니다");
                addFailedTestCaseDetails(judgeResult, result);
                break;
            case "TLE":
                result.addError("시간 초과가 발생했습니다");
                addFailedTestCaseDetails(judgeResult, result);
                break;
            case "MLE":
                result.addError("메모리 초과가 발생했습니다");
                addFailedTestCaseDetails(judgeResult, result);
                break;
            case "WA":
                result.addError(String.format("오답입니다 (통과: %d/%d, %.1f%%)",
                        judgeResult.getPassedCount(),
                        judgeResult.getTotalCount(),
                        judgeResult.getTestPassRate()));
                addFailedTestCaseDetails(judgeResult, result);
                break;
            default:
                result.addError("알 수 없는 실행 결과: " + overallResult);
        }
    }

    /**
     * 실패한 테스트케이스 상세 정보 추가
     */
    private void addFailedTestCaseDetails(TestRunResponseDto judgeResult, ValidationResultDto result) {
        if (judgeResult.getTestCaseResults() == null) {
            return;
        }

        List<TestRunResponseDto.TestCaseResultDto> failedCases = judgeResult.getTestCaseResults().stream()
                .filter(tc -> !"AC".equals(tc.getResult()) && !"PASS".equals(tc.getResult()))
                .limit(3)  // 최대 3개만 표시
                .collect(Collectors.toList());

        for (TestRunResponseDto.TestCaseResultDto failedCase : failedCases) {
            String detail = String.format("테스트케이스 %d: %s",
                    failedCase.getTestCaseNumber(),
                    failedCase.getErrorMessage() != null ? failedCase.getErrorMessage() : failedCase.getResult());
            result.addMetadata("failedCase_" + failedCase.getTestCaseNumber(), detail);
        }

        result.addMetadata("failedTestCases", failedCases.size());
    }
}
