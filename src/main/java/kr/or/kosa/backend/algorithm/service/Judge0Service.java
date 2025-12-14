package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.dto.AlgoTestcaseDto;
import kr.or.kosa.backend.algorithm.dto.enums.JudgeResult;
import kr.or.kosa.backend.algorithm.dto.request.Judge0RequestDto;
import kr.or.kosa.backend.algorithm.dto.response.Judge0ResponseDto;
import kr.or.kosa.backend.algorithm.dto.response.TestRunResponseDto;
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

/**
 * Judge0 API 연동 서비스 (ALG-07 관련)
 * AlgoTestcaseDto를 입력으로 받고 TestRunResponseDto를 반환
 *
 * 변경사항 (2025-12-13):
 * - LANGUAGES.LANGUAGE_ID가 Judge0 API ID이므로 매핑 로직 제거
 * - languageId (Integer)를 직접 사용
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
     * AlgoTestcaseDto 목록으로 채점 실행
     *
     * @param sourceCode  제출할 소스 코드
     * @param languageId  언어 ID (LANGUAGES.LANGUAGE_ID = Judge0 API language_id)
     * @param testCases   AlgoTestcaseDto 목록
     * @param timeLimit   시간 제한 (ms)
     * @param memoryLimit 메모리 제한 (KB)
     * @return 채점 결과 (TestRunResponseDto)
     */
    public CompletableFuture<TestRunResponseDto> judgeCode(
            String sourceCode,
            Integer languageId,
            List<AlgoTestcaseDto> testCases,
            Integer timeLimit,
            Integer memoryLimit) {

        return CompletableFuture.supplyAsync(() -> {
            log.info("Judge0 채점 시작 - languageId: {}, testCases: {}, timeLimit: {}ms, memoryLimit: {}MB",
                    languageId, testCases.size(), timeLimit, memoryLimit);

            if (languageId == null) {
                throw new IllegalArgumentException("언어 ID가 null입니다");
            }

            List<TestRunResponseDto.TestCaseResultDto> results = new ArrayList<>();
            int passedCount = 0;
            int maxExecutionTime = 0;
            int maxMemoryUsage = 0;

            for (int i = 0; i < testCases.size(); i++) {
                AlgoTestcaseDto testCase = testCases.get(i);

                try {
                    log.debug("테스트케이스 {} 실행 중...", i + 1);

                    TestRunResponseDto.TestCaseResultDto result = executeSingleTestCase(
                            sourceCode, languageId, testCase, i + 1, timeLimit, memoryLimit);

                    results.add(result);

                    if ("AC".equals(result.getResult()) || "PASS".equals(result.getResult())) {
                        passedCount++;
                    }

                    // 최대 실행 시간 및 메모리 사용량 계산
                    if (result.getExecutionTime() != null) {
                        maxExecutionTime = Math.max(maxExecutionTime, result.getExecutionTime());
                    }
                    if (result.getMemoryUsage() != null) {
                        maxMemoryUsage = Math.max(maxMemoryUsage, result.getMemoryUsage());
                    }

                    // Rate Limit 방지: 테스트케이스 간 딜레이 (RapidAPI 무료 요금제 제한)
                    if (i < testCases.size() - 1) {
                        Thread.sleep(1000); // 1초 대기
                    }

                } catch (Exception e) {
                    log.error("테스트케이스 {} 실행 중 오류", i + 1, e);

                    results.add(TestRunResponseDto.TestCaseResultDto.builder()
                            .testCaseNumber(i + 1)
                            .input(testCase.getInputData())
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

            return TestRunResponseDto.builder()
                    .overallResult(overallResult)
                    .passedCount(passedCount)
                    .totalCount(testCases.size())
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
    @Retryable(retryFor = { Exception.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    private TestRunResponseDto.TestCaseResultDto executeSingleTestCase(
            String sourceCode,
            Integer languageId,
            AlgoTestcaseDto testCase,
            Integer testCaseNumber,
            Integer timeLimit,
            Integer memoryLimit) {

        // 1. Judge0 요청 DTO
        // timeLimit (ms) -> cpu_time_limit (seconds) 변환 필요
        // Judge0는 초 단위 (float) 지원
        float cpuTimeLimitSec = timeLimit != null ? timeLimit / 1000.0f : this.cpuTimeLimit.floatValue();
        int memoryLimitKb = memoryLimit != null ? memoryLimit * 1000 : this.memoryLimit; // MB -> KB

        Judge0RequestDto request = Judge0RequestDto.builder()
                .source_code(sourceCode)
                .language_id(languageId)
                .stdin(testCase.getInputData())
                .expected_output(testCase.getExpectedOutput())
                .cpu_time_limit(cpuTimeLimitSec)
                .memory_limit(memoryLimitKb)
                .enable_per_process_and_thread_time_limit(true)
                .enable_per_process_and_thread_memory_limit(true)
                .build();

        log.info("[Judge0 Request Check] languageId={}, stdin='{}', expected_output='{}'",
                request.getLanguage_id(),
                truncateForLog(request.getStdin()),
                truncateForLog(request.getExpected_output()));
        log.debug("[Judge0 Source Code]\n{}", truncateForLog(request.getSource_code(), 500));

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
    private TestRunResponseDto.TestCaseResultDto interpretResult(
            Judge0ResponseDto response,
            AlgoTestcaseDto testCase,
            Integer testCaseNumber) {

        TestRunResponseDto.TestCaseResultDto.TestCaseResultDtoBuilder builder =
                TestRunResponseDto.TestCaseResultDto.builder()
                .testCaseNumber(testCaseNumber)
                .input(testCase.getInputData())
                .expectedOutput(testCase.getExpectedOutput())
                .actualOutput(response.getStdout())
                .executionTime(response.getTime() != null ? (int) Math.round(response.getTime() * 1000) : null)
                .memoryUsage(response.getMemory() != null ? response.getMemory().intValue() : null);

        // 컴파일 에러 확인
        if (response.getCompile_output() != null && !response.getCompile_output().trim().isEmpty()) {
            return builder
                    .result("CE")
                    .errorMessage("컴파일 에러: " + response.getCompile_output())
                    .build();
        }

        // 런타임 에러 확인
        if (response.getStderr() != null && !response.getStderr().trim().isEmpty()) {
            return builder
                    .result("RE")
                    .errorMessage("런타임 에러: " + response.getStderr())
                    .build();
        }

        // Judge0 상태 확인
        JudgeResult judgeResult = response.toJudgeResult();

        switch (judgeResult) {
            case AC:
                return builder.result("AC").build();
            case WA:
                return builder
                        .result("WA")
                        .errorMessage("출력이 예상 결과와 다릅니다")
                        .build();
            case TLE:
                return builder
                        .result("TLE")
                        .errorMessage("시간 초과")
                        .build();
            case MLE:
                return builder
                        .result("MLE")
                        .errorMessage("메모리 초과")
                        .build();
            case RE:
                return builder
                        .result("RE")
                        .errorMessage(
                                "런타임 에러: " + (response.getMessage() != null ? response.getMessage() : "알 수 없는 오류"))
                        .build();
            case CE:
                return builder
                        .result("CE")
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
    private String determineOverallResult(List<TestRunResponseDto.TestCaseResultDto> results, int passedCount, int totalCount) {
        if (passedCount == totalCount) {
            return "AC"; // Accepted
        }

        // 컴파일 에러 확인
        boolean hasCompileError = results.stream()
                .anyMatch(r -> "CE".equals(r.getResult()));
        if (hasCompileError) {
            return "CE";
        }

        // 런타임 에러 확인
        boolean hasRuntimeError = results.stream()
                .anyMatch(r -> "RE".equals(r.getResult()));
        if (hasRuntimeError) {
            return "RE";
        }

        // 시간 초과 확인
        boolean hasTimeLimit = results.stream()
                .anyMatch(r -> "TLE".equals(r.getResult()));
        if (hasTimeLimit) {
            return "TLE";
        }

        // 메모리 초과 확인
        boolean hasMemoryLimit = results.stream()
                .anyMatch(r -> "MLE".equals(r.getResult()));
        if (hasMemoryLimit) {
            return "MLE";
        }

        return "WA"; // Wrong Answer
    }

    /**
     * 로그용 문자열 잘라내기 (기본 100자)
     */
    private String truncateForLog(String text) {
        return truncateForLog(text, 100);
    }

    /**
     * 로그용 문자열 잘라내기
     */
    private String truncateForLog(String text, int maxLength) {
        if (text == null) {
            return "null";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...(총 " + text.length() + "자)";
    }
}
