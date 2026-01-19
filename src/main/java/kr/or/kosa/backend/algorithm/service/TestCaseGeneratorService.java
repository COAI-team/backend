package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.dto.AlgoTestcaseDto;
import kr.or.kosa.backend.algorithm.dto.LanguageDto;
import kr.or.kosa.backend.algorithm.dto.response.TestRunResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Code-First 방식 테스트케이스 생성 서비스
 *
 * LLM이 생성한 optimalCode를 실제 실행하여 테스트케이스의 expected output을 생성합니다.
 * 이 방식은 LeetCode, Codeforces, Google Code Jam 등 실제 코딩 테스트 플랫폼에서 사용하는 방식입니다.
 *
 * 참고:
 * - LeetCode: Problem Authoring Guide
 * - Codeforces: Polygon Problem Preparation System
 * - Google Code Jam: Problem Setting Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestCaseGeneratorService {

    private final CodeExecutorService codeExecutorService;
    private final LanguageService languageService;  // 언어 정보 조회

    @Value("${algorithm.testcase.epsilon:1e-9}")
    private double epsilon;

    @Value("${algorithm.testcase.timeout-ms:10000}")
    private long timeoutMs;

    /**
     * optimalCode를 실행하여 테스트케이스의 expected output을 생성합니다.
     *
     * @param optimalCode  최적 솔루션 코드
     * @param naiveCode    비효율적 솔루션 코드 (교차 검증용, nullable)
     * @param languageName 프로그래밍 언어명 (예: "Python 3", "Java 17")
     * @param testCases    입력 데이터만 있는 테스트케이스 목록
     * @return 출력 데이터가 채워진 테스트케이스 목록
     *
     * 변경사항 (2025-12-13): languageName → languageId 변환 후 CodeExecutorService 호출
     */
    public TestCaseGenerationResult generateOutputs(
            String optimalCode,
            String naiveCode,
            String languageName,
            List<AlgoTestcaseDto> testCases) {

        log.info("Code-First 테스트케이스 생성 시작 - {} 케이스, language: {}", testCases.size(), languageName);

        List<AlgoTestcaseDto> generatedTestCases = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int successCount = 0;
        int crossValidationFailCount = 0;

        // 언어명 → languageId 변환
        LanguageDto language = languageService.getByName(languageName);
        if (language == null) {
            log.error("지원하지 않는 프로그래밍 언어: {}", languageName);
            warnings.add("지원하지 않는 프로그래밍 언어: " + languageName);
            return new TestCaseGenerationResult(generatedTestCases, 0, 0, warnings);
        }
        Integer languageId = language.getLanguageId();

        // 1. optimalCode로 모든 테스트케이스 실행하여 출력 생성
        TestRunResponseDto optimalResults = executeCode(optimalCode, languageId, testCases);

        if (optimalResults == null) {
            log.error("optimalCode 실행 실패 - 전체 실행 오류");
            warnings.add("optimalCode 실행 전체 실패");
            return new TestCaseGenerationResult(generatedTestCases, 0, 0, warnings);
        }

        // 2. naiveCode 교차 검증 (있는 경우)
        TestRunResponseDto naiveResults = null;
        if (naiveCode != null && !naiveCode.isBlank()) {
            naiveResults = executeCode(naiveCode, languageId, testCases);
            if (naiveResults == null) {
                log.debug("naiveCode 실행 실패 (시간 초과 가능성)");
            }
        }

        // 3. 각 테스트케이스별 결과 처리
        List<TestRunResponseDto.TestCaseResultDto> optimalCaseResults = optimalResults.getTestCaseResults();

        for (int i = 0; i < testCases.size(); i++) {
            AlgoTestcaseDto originalTestCase = testCases.get(i);
            String input = originalTestCase.getInputData();

            if (i >= optimalCaseResults.size()) {
                warnings.add(String.format("케이스 %d: optimalCode 결과 없음", i + 1));
                continue;
            }

            TestRunResponseDto.TestCaseResultDto optimalCaseResult = optimalCaseResults.get(i);

            // 실행 오류 확인 (CE, RE 등)
            String result = optimalCaseResult.getResult();
            if ("CE".equals(result) || "RE".equals(result) || "ERROR".equals(result)) {
                log.warn("테스트케이스 {} - optimalCode 실행 실패: {} - {}",
                        i + 1, result, optimalCaseResult.getErrorMessage());
                warnings.add(String.format("케이스 %d: optimalCode 실행 실패 - %s",
                        i + 1, optimalCaseResult.getErrorMessage()));
                continue;
            }

            // TLE도 실패로 처리
            if ("TLE".equals(result)) {
                log.warn("테스트케이스 {} - optimalCode 시간 초과", i + 1);
                warnings.add(String.format("케이스 %d: optimalCode 시간 초과", i + 1));
                continue;
            }

            String optimalOutput = normalizeOutput(optimalCaseResult.getActualOutput());

            // 교차 검증 (naiveCode 결과가 있는 경우)
            if (naiveResults != null && naiveResults.getTestCaseResults() != null
                    && i < naiveResults.getTestCaseResults().size()) {

                TestRunResponseDto.TestCaseResultDto naiveCaseResult = naiveResults.getTestCaseResults().get(i);
                String naiveResult = naiveCaseResult.getResult();

                // naiveCode가 성공적으로 실행된 경우에만 비교
                if (!"CE".equals(naiveResult) && !"RE".equals(naiveResult)
                        && !"TLE".equals(naiveResult) && !"ERROR".equals(naiveResult)) {

                    String naiveOutput = normalizeOutput(naiveCaseResult.getActualOutput());

                    if (!compareOutputs(optimalOutput, naiveOutput)) {
                        log.warn("테스트케이스 {} - 교차 검증 실패! optimal: '{}', naive: '{}'",
                                i + 1,
                                truncate(optimalOutput, 50),
                                truncate(naiveOutput, 50));
                        warnings.add(String.format("케이스 %d: 교차 검증 실패 (optimal=%s, naive=%s)",
                                i + 1, truncate(optimalOutput, 30), truncate(naiveOutput, 30)));
                        crossValidationFailCount++;
                        // 교차 검증 실패해도 optimalCode 결과 사용 (경고만 추가)
                    } else {
                        log.debug("테스트케이스 {} - 교차 검증 통과", i + 1);
                    }
                } else {
                    log.debug("테스트케이스 {} - naiveCode 실행 실패/시간초과 (예상 동작)", i + 1);
                }
            }

            // 테스트케이스 생성
            AlgoTestcaseDto generatedTestCase = AlgoTestcaseDto.builder()
                    .inputData(input)
                    .expectedOutput(optimalOutput)
                    .isSample(originalTestCase.getIsSample())
                    .build();

            generatedTestCases.add(generatedTestCase);
            successCount++;
            log.debug("테스트케이스 {} 생성 완료 - 출력: {}", i + 1, truncate(optimalOutput, 50));
        }

        log.info("Code-First 테스트케이스 생성 완료 - 성공: {}/{}, 교차검증 실패: {}, 경고: {}",
                successCount, testCases.size(), crossValidationFailCount, warnings.size());

        return new TestCaseGenerationResult(
                generatedTestCases,
                successCount,
                crossValidationFailCount,
                warnings
        );
    }

    /**
     * 코드 실행 (judgeCode 활용)
     * 더미 expectedOutput으로 실행하여 actualOutput 추출
     *
     * @param code       실행할 코드
     * @param languageId 언어 ID (LANGUAGES.LANGUAGE_ID)
     * @param testCases  테스트케이스 목록
     */
    private TestRunResponseDto executeCode(String code, Integer languageId, List<AlgoTestcaseDto> testCases) {
        try {
            // 더미 expectedOutput을 설정한 테스트케이스 생성
            List<AlgoTestcaseDto> testCasesWithDummy = testCases.stream()
                    .map(tc -> AlgoTestcaseDto.builder()
                            .inputData(tc.getInputData())
                            .expectedOutput("__DUMMY_OUTPUT__")  // 더미 값
                            .isSample(tc.getIsSample())
                            .build())
                    .toList();

            CompletableFuture<TestRunResponseDto> future = codeExecutorService.judgeCode(
                    code, languageId, testCasesWithDummy, (int) timeoutMs, 256 * 1024);

            return future.get(timeoutMs + 5000, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            log.error("코드 실행 중 오류: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 출력 문자열 정규화
     */
    private String normalizeOutput(String output) {
        if (output == null) return "";

        return output
                .trim()
                .replaceAll("[ \\t]+$", "") // 줄 끝 공백 제거
                .replaceAll("\\r\\n", "\n") // Windows 줄바꿈 통일
                .replaceAll("\\r", "\n");
    }

    /**
     * 두 출력 비교 (부동소수점 허용 오차 포함)
     */
    private boolean compareOutputs(String output1, String output2) {
        if (output1.equals(output2)) {
            return true;
        }

        // 부동소수점 비교 시도
        try {
            double d1 = Double.parseDouble(output1.trim());
            double d2 = Double.parseDouble(output2.trim());
            return Math.abs(d1 - d2) < epsilon;
        } catch (NumberFormatException e) {
            // 숫자가 아닌 경우 단순 문자열 비교
            return output1.trim().equals(output2.trim());
        }
    }

    /**
     * 문자열 truncate
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "...";
    }

    /**
     * 테스트케이스 생성 결과
     */
    public record TestCaseGenerationResult(
            List<AlgoTestcaseDto> testCases,
            int successCount,
            int crossValidationFailCount,
            List<String> warnings
    ) {
        public boolean isFullySuccessful() {
            return warnings.isEmpty() && crossValidationFailCount == 0;
        }

        public boolean hasMinimumTestCases(int minimum) {
            return testCases.size() >= minimum;
        }

        public double getSuccessRate() {
            if (testCases.isEmpty()) return 0.0;
            return (double) successCount / testCases.size() * 100;
        }
    }
}
