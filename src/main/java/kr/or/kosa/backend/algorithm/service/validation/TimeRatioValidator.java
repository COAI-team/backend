package kr.or.kosa.backend.algorithm.service.validation;

import kr.or.kosa.backend.algorithm.dto.AlgoTestcaseDto;
import kr.or.kosa.backend.algorithm.dto.LanguageDto;
import kr.or.kosa.backend.algorithm.dto.ValidationResultDto;
import kr.or.kosa.backend.algorithm.dto.response.TestRunResponseDto;
import kr.or.kosa.backend.algorithm.service.CodeExecutorService;
import kr.or.kosa.backend.algorithm.service.LanguageService;
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
    private final LanguageService languageService;          // 언어 정보 조회

    @Value("${algorithm.validation.min-time-ratio:1.5}")
    private double minTimeRatio;

    @Value("${algorithm.validation.execution-timeout:30}")
    private int executionTimeoutSeconds;

    @Value("${algorithm.validation.default-time-limit:5000}")
    private int defaultTimeLimit;

    @Value("${algorithm.validation.default-memory-limit:256}")
    private int defaultMemoryLimit;

    // ===== Phase 7: 출력 일관성 검증 설정 =====
    @Value("${algorithm.validation.output-consistency.enabled:true}")
    private boolean outputConsistencyEnabled;

    @Value("${algorithm.validation.output-consistency.sample-limit:2}")
    private int sampleLimit;

    @Value("${algorithm.validation.output-consistency.timeout-ms:5000}")
    private int outputConsistencyTimeoutMs;

    /**
     * 시간 비율 검증
     *
     * @param optimalCode  최적 풀이 코드
     * @param naiveCode    비효율 풀이 코드
     * @param languageName 프로그래밍 언어명 (예: "Python 3", "Java 17")
     * @param testCases    테스트케이스 목록
     * @param timeLimit    시간 제한 (ms, nullable)
     * @param memoryLimit  메모리 제한 (MB, nullable)
     * @return 검증 결과
     *
     * 변경사항 (2025-12-13): languageName → languageId 변환 후 CodeExecutorService 호출
     */
    public ValidationResultDto validate(
            String optimalCode,
            String naiveCode,
            String languageName,
            List<AlgoTestcaseDto> testCases,
            Integer timeLimit,
            Integer memoryLimit) {

        log.info("시간 비율 검증 시작 - language: {}, minTimeRatio: {}", languageName, minTimeRatio);

        ValidationResultDto result = ValidationResultDto.builder()
                .passed(true)
                .validatorName(VALIDATOR_NAME)
                .build();

        if (!validateInputs(optimalCode, naiveCode, languageName, testCases, result)) {
            return result;
        }

        // 언어명 → languageId 변환
        LanguageDto language = languageService.getByName(languageName);
        if (language == null) {
            result.addError("지원하지 않는 프로그래밍 언어입니다: " + languageName);
            return result;
        }
        Integer languageId = language.getLanguageId();

        int effectiveTimeLimit = timeLimit != null && timeLimit > 0 ? timeLimit * 2 : defaultTimeLimit;
        int effectiveMemoryLimit = memoryLimit != null && memoryLimit > 0 ? memoryLimit : defaultMemoryLimit;

        try {
            // ===== Phase 7: 출력 일관성 검증 (방안 B) =====
            // 시간 비율 검증 전에 샘플 TC로 optimal과 naive의 출력이 일치하는지 확인
            if (outputConsistencyEnabled) {
                validateSampleOutputConsistency(optimalCode, naiveCode, languageId, testCases, result);
                if (!result.isPassed()) {
                    log.warn("출력 일관성 검증 실패 - 시간 비율 검증 스킵");
                    return result;
                }
            }

            // ===== 기존 시간 비율 검증 (방안 C 포함) =====
            long optimalTime = executeAndGetMaxTime(optimalCode, languageId, testCases,
                    effectiveTimeLimit, effectiveMemoryLimit, result, "optimal");

            if (optimalTime < 0) {
                return result;
            }

            long naiveTime = executeAndGetMaxTime(naiveCode, languageId, testCases,
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
            Integer languageId,
            List<AlgoTestcaseDto> testCases,
            int timeLimit,
            int memoryLimit,
            ValidationResultDto result,
            String codeType) {

        try {
            TestRunResponseDto judgeResult = codeExecutorService.judgeCode(
                    code, languageId, testCases, timeLimit, memoryLimit
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

    // ===== Phase 7: 출력 일관성 검증 메서드들 =====

    /**
     * 샘플 테스트케이스에서 optimal과 naive의 출력 일관성 검증 (방안 B)
     *
     * 핵심 로직:
     * 1. 샘플 TC만 추출 (최대 sampleLimit개)
     * 2. 각 샘플 TC에 대해 optimal과 naive 코드를 실행
     * 3. 두 출력이 일치하는지 검증 (naive TLE는 허용)
     *
     * @param optimalCode optimal 풀이 코드
     * @param naiveCode   naive 풀이 코드
     * @param languageId  언어 ID
     * @param testCases   전체 테스트케이스 목록
     * @param result      검증 결과 객체
     */
    private void validateSampleOutputConsistency(
            String optimalCode,
            String naiveCode,
            Integer languageId,
            List<AlgoTestcaseDto> testCases,
            ValidationResultDto result) {

        log.info("출력 일관성 검증 시작 - sampleLimit: {}, timeout: {}ms", sampleLimit, outputConsistencyTimeoutMs);

        // 1. 샘플 TC만 추출 (isSample = true)
        List<AlgoTestcaseDto> sampleCases = testCases.stream()
                .filter(tc -> Boolean.TRUE.equals(tc.getIsSample()))
                .limit(sampleLimit)
                .toList();

        if (sampleCases.isEmpty()) {
            log.warn("샘플 테스트케이스가 없어 출력 일관성 검증을 건너뜁니다");
            result.addWarning("샘플 테스트케이스가 없어 출력 일관성 검증을 건너뛰었습니다");
            result.addMetadata("outputConsistencySkipped", true);
            result.addMetadata("skipReason", "NO_SAMPLE_TESTCASES");
            return;
        }

        log.info("출력 일관성 검증 대상 샘플 TC 수: {}", sampleCases.size());
        int checkedCount = 0;
        int mismatchCount = 0;

        // 2. 각 샘플 TC에 대해 출력 비교
        for (int i = 0; i < sampleCases.size(); i++) {
            AlgoTestcaseDto sample = sampleCases.get(i);
            String inputData = sample.getInputData();

            log.debug("샘플 TC {} 검증 중 - 입력 길이: {} chars", i + 1, inputData != null ? inputData.length() : 0);

            // optimal 코드 실행
            String optimalOutput = executeSingleTestCase(optimalCode, languageId, inputData, outputConsistencyTimeoutMs);
            if (optimalOutput == null) {
                result.addError(String.format("샘플 TC %d: optimal 코드 실행 실패", i + 1));
                continue;
            }

            // naive 코드 실행
            String naiveOutput = executeSingleTestCase(naiveCode, languageId, inputData, outputConsistencyTimeoutMs);

            // naive TLE는 허용 (null 반환 시 건너뜀)
            if (naiveOutput == null) {
                log.info("샘플 TC {}: naive 코드 TLE 또는 실행 실패 - 허용 (건너뜀)", i + 1);
                result.addMetadata("sample_" + (i + 1) + "_naiveTLE", true);
                continue;
            }

            checkedCount++;

            // 출력 정규화 후 비교
            String normalizedOptimal = normalizeOutput(optimalOutput);
            String normalizedNaive = normalizeOutput(naiveOutput);

            if (!normalizedOptimal.equals(normalizedNaive)) {
                mismatchCount++;
                result.addError(String.format(
                        "샘플 TC %d: optimal과 naive의 출력이 일치하지 않습니다. optimal='%s', naive='%s'",
                        i + 1, truncate(normalizedOptimal, 50), truncate(normalizedNaive, 50)));
                result.addMetadata("sample_" + (i + 1) + "_mismatch", true);
                log.warn("출력 불일치 - 샘플 TC {}: optimal='{}', naive='{}'",
                        i + 1, truncate(normalizedOptimal, 100), truncate(normalizedNaive, 100));
            } else {
                log.debug("샘플 TC {} 출력 일치 확인", i + 1);
                result.addMetadata("sample_" + (i + 1) + "_matched", true);
            }
        }

        // 3. 최종 결과 기록
        result.addMetadata("outputConsistencyChecked", checkedCount);
        result.addMetadata("outputConsistencyMismatch", mismatchCount);

        if (mismatchCount > 0) {
            result.setPassed(false);
            log.warn("출력 일관성 검증 실패 - {} / {} 샘플에서 불일치 발견", mismatchCount, checkedCount);
        } else {
            log.info("출력 일관성 검증 성공 - {} 샘플 모두 일치 (또는 naive TLE)", checkedCount);
        }
    }

    /**
     * 단일 테스트케이스 실행 (출력 일관성 검증용)
     *
     * @param code       실행할 코드
     * @param languageId 언어 ID
     * @param input      입력 데이터
     * @param timeoutMs  타임아웃 (밀리초)
     * @return 실행 결과 출력 (실패 시 null)
     */
    private String executeSingleTestCase(String code, Integer languageId, String input, int timeoutMs) {
        try {
            // 단일 TC를 위한 임시 DTO 생성
            AlgoTestcaseDto tempTestCase = new AlgoTestcaseDto();
            tempTestCase.setInputData(input);
            tempTestCase.setExpectedOutput("");  // 출력 검증이 아닌 실행 결과 확인용

            // 코드 실행 (타임아웃 적용)
            TestRunResponseDto judgeResult = codeExecutorService.judgeCode(
                    code, languageId, List.of(tempTestCase), timeoutMs, defaultMemoryLimit
            ).get(executionTimeoutSeconds, TimeUnit.SECONDS);

            // 실행 실패 체크 (TLE 포함)
            if (judgeResult == null || judgeResult.getTestCaseResults() == null
                    || judgeResult.getTestCaseResults().isEmpty()) {
                log.debug("코드 실행 결과 없음 (TLE 또는 오류)");
                return null;
            }

            // 첫 번째 TC 결과의 실제 출력 반환
            var tcResult = judgeResult.getTestCaseResults().get(0);
            if (tcResult == null) {
                return null;
            }

            // TLE나 RE인 경우 null 반환
            String result = tcResult.getResult();
            if ("TLE".equals(result) || "Time Limit Exceeded".equals(result)) {
                log.debug("TLE 발생");
                return null;
            }
            if (result != null && (result.contains("Error") || result.contains("Runtime"))) {
                log.debug("런타임 에러: {}", result);
                return null;
            }

            return tcResult.getActualOutput();

        } catch (Exception e) {
            log.debug("단일 TC 실행 중 예외: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 출력 문자열 정규화 (비교를 위해)
     * - 앞뒤 공백 제거
     * - 줄 끝 공백 제거
     * - 연속 공백을 단일 공백으로
     * - 줄바꿈 정규화 (\r\n -> \n)
     */
    private String normalizeOutput(String output) {
        if (output == null) {
            return "";
        }
        return output
                .replaceAll("\\r\\n", "\n")     // Windows 줄바꿈 정규화
                .replaceAll("[ \\t]+$", "")      // 줄 끝 공백 제거 (multiline)
                .replaceAll("(?m)[ \\t]+$", "") // 각 줄 끝 공백 제거
                .trim();                         // 앞뒤 공백 제거
    }

    /**
     * 문자열 자르기 (로그/에러 메시지용)
     */
    private String truncate(String str, int maxLength) {
        if (str == null) {
            return "null";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }
}
