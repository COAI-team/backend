package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.domain.AlgoSubmission;
import kr.or.kosa.backend.algorithm.domain.AlgoTestcase;
import kr.or.kosa.backend.algorithm.domain.ProgrammingLanguage;
import kr.or.kosa.backend.algorithm.dto.Judge0RequestDto;
import kr.or.kosa.backend.algorithm.dto.Judge0ResponseDto;
import kr.or.kosa.backend.algorithm.dto.SubmissionResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Judge0 API 연동 서비스 (ALG-07 관련) - 수정 버전
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Judge0Service {

    private final WebClient judge0WebClient;

    // application.yml에서 직접 값 주입
    @Value("${judge0.api.base-url}")
    private String baseUrl;

    @Value("${judge0.api.rapidapi-key}")
    private String apiKey;

    @Value("${judge0.api.limits.cpu-time:2.0}")
    private Double cpuTimeLimit;

    @Value("${judge0.api.limits.memory:128000}")
    private Integer memoryLimit;

    /**
     * 프로그래밍 언어를 Judge0 언어 ID로 매핑
     */
    private Integer getLanguageId(ProgrammingLanguage language) {
        switch (language) {
            case JAVA:
                return 62; // Java (OpenJDK 13.0.1)
            case PYTHON:
                return 71; // Python (3.8.1)
            case CPP:
                return 54; // C++ (GCC 9.2.0)
            case JAVASCRIPT:
                return 63; // JavaScript (Node.js 12.14.0)
            case C:
                return 50; // C (GCC 9.2.0)
            default:
                throw new IllegalArgumentException("지원하지 않는 언어입니다: " + language);
        }
    }

    /**
     * 모든 테스트케이스에 대해 순차적으로 채점 실행
     */
    public CompletableFuture<JudgeResultDto> judgeCode(
            String sourceCode,
            ProgrammingLanguage language,
            List<TestCaseDto> testCases) {

        return CompletableFuture.supplyAsync(() -> {
            log.info("Judge0 채점 시작 - language: {}, testCases: {}", language, testCases.size());

            List<TestCaseResultDto> results = new ArrayList<>();
            Integer languageId = getLanguageId(language);
            int passedCount = 0;
            int maxExecutionTime = 0;
            int maxMemoryUsage = 0;

            for (int i = 0; i < testCases.size(); i++) {
                TestCaseDto testCase = testCases.get(i);

                try {
                    log.debug("테스트케이스 {} 실행 중...", i + 1);

                    TestCaseResultDto result = executeSingleTestCase(
                            sourceCode, languageId, testCase, i + 1);

                    results.add(result);

                    if ("PASS".equals(result.getResult())) {
                        passedCount++;
                    }

                    // 최대 실행 시간 및 메모리 사용량 계산
                    if (result.getExecutionTime() != null) {
                        maxExecutionTime = Math.max(maxExecutionTime, result.getExecutionTime());
                    }
                    if (result.getMemoryUsage() != null) {
                        maxMemoryUsage = Math.max(maxMemoryUsage, result.getMemoryUsage());
                    }

                } catch (Exception e) {
                    log.error("테스트케이스 {} 실행 중 오류", i + 1, e);

                    results.add(TestCaseResultDto.builder()
                            .testCaseNumber(i + 1)
                            .input(testCase.getInput())
                            .expectedOutput(testCase.getExpectedOutput())
                            .result("ERROR")
                            .errorMessage("채점 서버 오류: " + e.getMessage())
                            .build());
                }
            }

            // 전체 결과 판정
            String overallResult = determineOverallResult(results, passedCount, testCases.size());

            log.info("Judge0 채점 완료 - 총 {} 케이스, 통과 {} 케이스, 결과: {}",
                    results.size(), passedCount, overallResult);

            return JudgeResultDto.builder()
                    .overallResult(overallResult)
                    .passedTestCount(passedCount)
                    .totalTestCount(testCases.size())
                    .testPassRate((double) passedCount / testCases.size() * 100.0)
                    .maxExecutionTime(maxExecutionTime)
                    .maxMemoryUsage(maxMemoryUsage)
                    .testCaseResults(results)
                    .build();
        });
    }

    /**
     * 단일 테스트케이스 실행
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    private TestCaseResultDto executeSingleTestCase(
            String sourceCode,
            Integer languageId,
            TestCaseDto testCase,
            Integer testCaseNumber) {

        // 1. Judge0 요청 DTO
        Judge0RequestDto request = Judge0RequestDto.builder()
                .source_code(sourceCode)
                .language_id(languageId)
                .stdin(testCase.getInput())
                .expected_output(testCase.getExpectedOutput())
                .cpu_time_limit(cpuTimeLimit.intValue())
                .memory_limit(memoryLimit)
                .enable_per_process_and_thread_time_limit(true)
                .enable_per_process_and_thread_memory_limit(true)
                .build();

        log.info("[Judge0 Request Check] {}", request);

        try {
            // 2. WebClient로 Judge0에 제출
            Judge0ResponseDto response = judge0WebClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/submissions")
                            .queryParam("base64_encoded", false)
                            .queryParam("wait", true)
                            .build())
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Judge0ResponseDto.class)
                    .block(); // 동기 호출

            if (response == null) {
                throw new RuntimeException("Judge0 응답이 비어있습니다");
            }

            log.info("[Judge0 Raw Response] stdout={}, stderr={}, compile_output={}, status={}",
                    response.getStdout(),
                    response.getStderr(),
                    response.getCompile_output(),
                    response.getStatus());

            // 3. 결과 해석
            return interpretResult(response, testCase, testCaseNumber);

        } catch (Exception e) {
            log.error("Judge0 API 호출 실패", e);
            throw new RuntimeException("Judge0 채점 실패: " + e.getMessage(), e);
        }
    }


    /**
     * Judge0 응답 결과 해석
     */
    private TestCaseResultDto interpretResult(
            Judge0ResponseDto response,
            TestCaseDto testCase,
            Integer testCaseNumber) {

        TestCaseResultDto.TestCaseResultDtoBuilder builder =
                TestCaseResultDto.builder()
                        .testCaseNumber(testCaseNumber)
                        .input(testCase.getInput())
                        .expectedOutput(testCase.getExpectedOutput())
                        .actualOutput(response.getStdout())
                        .executionTime(response.getTime() != null ? (int) Math.round(response.getTime() * 1000) : null)
                        .memoryUsage(response.getMemory() != null ? response.getMemory().intValue() : null);

        // 컴파일 에러 확인
        if (response.getCompile_output() != null && !response.getCompile_output().trim().isEmpty()) {
            return builder
                    .result("ERROR")
                    .errorMessage("컴파일 에러: " + response.getCompile_output())
                    .build();
        }

        // 런타임 에러 확인
        if (response.getStderr() != null && !response.getStderr().trim().isEmpty()) {
            return builder
                    .result("ERROR")
                    .errorMessage("런타임 에러: " + response.getStderr())
                    .build();
        }

        // Judge0 상태 확인
        AlgoSubmission.JudgeResult judgeResult = response.toJudgeResult();

        switch (judgeResult) {
            case AC:
                return builder.result("PASS").build();
            case WA:
                return builder
                        .result("FAIL")
                        .errorMessage("출력이 예상 결과와 다릅니다")
                        .build();
            case TLE:
                return builder
                        .result("ERROR")
                        .errorMessage("시간 초과")
                        .build();
            case MLE:
                return builder
                        .result("ERROR")
                        .errorMessage("메모리 초과")
                        .build();
            case RE:
                return builder
                        .result("ERROR")
                        .errorMessage("런타임 에러: " + (response.getMessage() != null ? response.getMessage() : "알 수 없는 오류"))
                        .build();
            case CE:
                return builder
                        .result("ERROR")
                        .errorMessage("컴파일 에러")
                        .build();
            default:
                return builder
                        .result("ERROR")
                        .errorMessage("알 수 없는 오류")
                        .build();
        }
    }

    /**
     * 전체 결과 판정
     */
    private String determineOverallResult(List<TestCaseResultDto> results, int passedCount, int totalCount) {
        if (passedCount == totalCount) {
            return "AC"; // Accepted
        }

        // 컴파일 에러 확인
        boolean hasCompileError = results.stream()
                .anyMatch(r -> "ERROR".equals(r.getResult()) &&
                        r.getErrorMessage() != null && r.getErrorMessage().contains("컴파일 에러"));
        if (hasCompileError) {
            return "CE";
        }

        // 런타임 에러 확인
        boolean hasRuntimeError = results.stream()
                .anyMatch(r -> "ERROR".equals(r.getResult()) &&
                        r.getErrorMessage() != null && r.getErrorMessage().contains("런타임 에러"));
        if (hasRuntimeError) {
            return "RE";
        }

        // 시간 초과 확인
        boolean hasTimeLimit = results.stream()
                .anyMatch(r -> "ERROR".equals(r.getResult()) &&
                        r.getErrorMessage() != null && r.getErrorMessage().contains("시간 초과"));
        if (hasTimeLimit) {
            return "TLE";
        }

        return "WA"; // Wrong Answer
    }

    // DTO 클래스들

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TestCaseDto {
        private String input;
        private String expectedOutput;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class JudgeResultDto {
        private String overallResult;
        private Integer passedTestCount;
        private Integer totalTestCount;
        private Double testPassRate;
        private Integer maxExecutionTime;
        private Integer maxMemoryUsage;
        private List<TestCaseResultDto> testCaseResults;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TestCaseResultDto {
        private Integer testCaseNumber;
        private String input;
        private String expectedOutput;
        private String actualOutput;
        private String result;
        private Integer executionTime;
        private Integer memoryUsage;
        private String errorMessage;
    }
}