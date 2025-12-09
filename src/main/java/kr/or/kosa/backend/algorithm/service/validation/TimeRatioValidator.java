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

/**
 * Phase 4-3: 시간 비율 검사기
 * 최적 풀이와 비효율 풀이의 실행 시간 비율을 검사
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimeRatioValidator {

    private static final String VALIDATOR_NAME = "TimeRatioValidator";

    private final CodeExecutorService codeExecutorService;  // Judge0 또는 Piston 선택

    @Value("${algorithm.validation.min-time-ratio:1.5}")
    private double minTimeRatio;

    @Value("${algorithm.validation.execution-timeout:30}")
    private int executionTimeoutSeconds;

    @Value("${algorithm.validation.default-time-limit:5000}")
    private int defaultTimeLimit;

    @Value("${algorithm.validation.default-memory-limit:256}")
    private int defaultMemoryLimit;

    public ValidationResultDto validate(
            String optimalCode,
            String naiveCode,
            String language,
            List<AlgoTestcaseDto> testCases,
            Integer timeLimit,
            Integer memoryLimit) {

        log.info("시간 비율 검증 시작 - language: {}, minTimeRatio: {}", language, minTimeRatio);

        ValidationResultDto result = ValidationResultDto.builder()
                .passed(true)
                .validatorName(VALIDATOR_NAME)
                .build();

        if (!validateInputs(optimalCode, naiveCode, language, testCases, result)) {
            return result;
        }

        int effectiveTimeLimit = timeLimit != null && timeLimit > 0 ? timeLimit * 2 : defaultTimeLimit;
        int effectiveMemoryLimit = memoryLimit != null && memoryLimit > 0 ? memoryLimit : defaultMemoryLimit;

        try {
            long optimalTime = executeAndGetMaxTime(optimalCode, language, testCases,
                    effectiveTimeLimit, effectiveMemoryLimit, result, "optimal");

            if (optimalTime < 0) {
                return result;
            }

            long naiveTime = executeAndGetMaxTime(naiveCode, language, testCases,
                    effectiveTimeLimit, effectiveMemoryLimit, result, "naive");

            if (naiveTime < 0) {
                return result;
            }

            analyzeTimeRatio(optimalTime, naiveTime, result);

        } catch (Exception e) {
            log.error("시간 비율 검증 중 오류 발생", e);
            result.addError("시간 비율 검증 중 오류 발생: " + e.getMessage());
        }

        log.info("시간 비율 검증 완료 - 결과: {}", result.getSummary());
        return result;
    }

    private boolean validateInputs(
            String optimalCode,
            String naiveCode,
            String language,
            List<AlgoTestcaseDto> testCases,
            ValidationResultDto result) {

        if (optimalCode == null || optimalCode.isBlank()) {
            result.addWarning("최적 풀이 코드가 없어 시간 비율 검증을 건너뜁니다");
            result.addMetadata("skipped", true);
            result.addMetadata("skipReason", "NO_OPTIMAL_CODE");
            return false;
        }

        if (naiveCode == null || naiveCode.isBlank()) {
            result.addWarning("비효율 풀이 코드가 없어 시간 비율 검증을 건너뜁니다");
            result.addMetadata("skipped", true);
            result.addMetadata("skipReason", "NO_NAIVE_CODE");
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

    private long executeAndGetMaxTime(
            String code,
            String language,
            List<AlgoTestcaseDto> testCases,
            int timeLimit,
            int memoryLimit,
            ValidationResultDto result,
            String codeType) {

        try {
            TestRunResponseDto judgeResult = codeExecutorService.judgeCode(
                    code, language, testCases, timeLimit, memoryLimit
            ).get(executionTimeoutSeconds, TimeUnit.SECONDS);

            if (!"AC".equals(judgeResult.getOverallResult())) {
                if ("TLE".equals(judgeResult.getOverallResult()) && "naive".equals(codeType)) {
                    log.info("비효율 풀이가 시간 초과됨 - 시간 비율 검증에 유리");
                    result.addMetadata("naiveTimedOut", true);
                    return timeLimit;
                }

                result.addError(String.format("%s 풀이 실행 실패: %s (통과율: %.1f%%)",
                        "optimal".equals(codeType) ? "최적" : "비효율",
                        judgeResult.getOverallResult(),
                        judgeResult.getTestPassRate()));
                return -1;
            }

            long maxExecutionTime = judgeResult.getMaxExecutionTime() != null
                    ? judgeResult.getMaxExecutionTime() : 0;
            result.addMetadata(codeType + "MaxTime", maxExecutionTime);

            log.info("{} 풀이 실행 완료 - 최대 실행 시간: {}ms",
                    "optimal".equals(codeType) ? "최적" : "비효율", maxExecutionTime);

            return maxExecutionTime;

        } catch (Exception e) {
            log.error("{} 풀이 실행 중 오류", codeType, e);
            result.addError(String.format("%s 풀이 실행 중 오류: %s",
                    "optimal".equals(codeType) ? "최적" : "비효율", e.getMessage()));
            return -1;
        }
    }

    private void analyzeTimeRatio(long optimalTime, long naiveTime, ValidationResultDto result) {
        long adjustedOptimalTime = Math.max(optimalTime, 1);
        long adjustedNaiveTime = Math.max(naiveTime, 1);

        double timeRatio = (double) adjustedNaiveTime / adjustedOptimalTime;

        result.addMetadata("optimalTime", optimalTime);
        result.addMetadata("naiveTime", naiveTime);
        result.addMetadata("timeRatio", Math.round(timeRatio * 100) / 100.0);
        result.addMetadata("minRequiredRatio", minTimeRatio);

        log.info("시간 비율 분석 - 최적: {}ms, 비효율: {}ms, 비율: {}x (최소 요구: {}x)",
                optimalTime, naiveTime, String.format("%.2f", timeRatio), minTimeRatio);

        if (timeRatio < minTimeRatio) {
            result.addWarning(String.format(
                    "시간 비율이 기준에 미달합니다 (현재: %.2fx, 최소: %.2fx). " +
                    "비효율 풀이가 충분히 느리지 않아 시간복잡도 구분이 명확하지 않을 수 있습니다.",
                    timeRatio, minTimeRatio));
        }
    }
}
