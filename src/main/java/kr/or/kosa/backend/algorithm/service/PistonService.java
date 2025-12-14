package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.dto.AlgoTestcaseDto;
import kr.or.kosa.backend.algorithm.dto.response.TestRunResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Piston API 연동 서비스
 * 로컬 개발 환경에서 Judge0 대신 사용
 * 공개 API: https://emkc.org/api/v2/piston
 *
 * 변경사항 (2025-12-13):
 * - LANGUAGES.PISTON_LANGUAGE를 직접 사용하므로 매핑 로직 제거
 * - pistonLanguage (String)을 직접 전달받음
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PistonService {

    private final WebClient pistonWebClient;

    @Value("${piston.api.timeout:30000}")
    private Integer timeout;

    /**
     * AlgoTestcaseDto 목록으로 채점 실행
     *
     * @param sourceCode     제출할 소스 코드
     * @param pistonLanguage Piston API 언어명 (LANGUAGES.PISTON_LANGUAGE)
     * @param testCases      AlgoTestcaseDto 목록
     * @param timeLimit      시간 제한 (ms)
     * @param memoryLimit    메모리 제한 (KB)
     * @return 채점 결과 (TestRunResponseDto)
     */
    public CompletableFuture<TestRunResponseDto> judgeCode(
            String sourceCode,
            String pistonLanguage,
            List<AlgoTestcaseDto> testCases,
            Integer timeLimit,
            Integer memoryLimit) {

        return CompletableFuture.supplyAsync(() -> {
            log.info("Piston 채점 시작 - pistonLanguage: {}, testCases: {}", pistonLanguage, testCases.size());

            if (pistonLanguage == null || pistonLanguage.isBlank()) {
                throw new IllegalArgumentException("Piston 언어명이 null 또는 빈 문자열입니다");
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
                            sourceCode, pistonLanguage, testCase, i + 1);

                    results.add(result);

                    if ("AC".equals(result.getResult()) || "PASS".equals(result.getResult())) {
                        passedCount++;
                    }

                    if (result.getExecutionTime() != null) {
                        maxExecutionTime = Math.max(maxExecutionTime, result.getExecutionTime());
                    }
                    if (result.getMemoryUsage() != null) {
                        maxMemoryUsage = Math.max(maxMemoryUsage, result.getMemoryUsage());
                    }

                } catch (Exception e) {
                    log.error("테스트케이스 {} 실행 중 오류", i + 1, e);

                    results.add(TestRunResponseDto.TestCaseResultDto.builder()
                            .testCaseNumber(i + 1)
                            .input(testCase.getInputData())
                            .expectedOutput(testCase.getExpectedOutput())
                            .result("ERROR")
                            .errorMessage("Piston API 오류: " + e.getMessage())
                            .build());
                }
            }

            String overallResult = determineOverallResult(results, passedCount, testCases.size());

            log.info("Piston 채점 완료 - 총 {} 케이스, 통과 {} 케이스, 결과: {}",
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
    private TestRunResponseDto.TestCaseResultDto executeSingleTestCase(
            String sourceCode,
            String pistonLanguage,
            AlgoTestcaseDto testCase,
            Integer testCaseNumber) {

        long startTime = System.currentTimeMillis();

        // Piston API 요청 생성
        Map<String, Object> request = Map.of(
                "language", pistonLanguage,
                "version", "*",  // 최신 버전 사용
                "files", List.of(Map.of("content", sourceCode)),
                "stdin", testCase.getInputData() != null ? testCase.getInputData() : ""
        );

        try {
            // Piston API 호출
            @SuppressWarnings("unchecked")
            Map<String, Object> response = pistonWebClient.post()
                    .uri("/execute")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            long executionTime = System.currentTimeMillis() - startTime;

            if (response == null) {
                throw new RuntimeException("Piston 응답이 비어있습니다");
            }

            log.debug("Piston 응답: {}", response);

            return interpretResult(response, testCase, testCaseNumber, (int) executionTime);

        } catch (Exception e) {
            log.error("Piston API 호출 실패", e);
            throw new RuntimeException("Piston 실행 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Piston 응답 결과 해석
     */
    @SuppressWarnings("unchecked")
    private TestRunResponseDto.TestCaseResultDto interpretResult(
            Map<String, Object> response,
            AlgoTestcaseDto testCase,
            Integer testCaseNumber,
            Integer executionTime) {

        TestRunResponseDto.TestCaseResultDto.TestCaseResultDtoBuilder builder =
                TestRunResponseDto.TestCaseResultDto.builder()
                        .testCaseNumber(testCaseNumber)
                        .input(testCase.getInputData())
                        .expectedOutput(testCase.getExpectedOutput())
                        .executionTime(executionTime);

        // 컴파일 단계 확인
        Map<String, Object> compile = (Map<String, Object>) response.get("compile");
        if (compile != null) {
            String compileStderr = (String) compile.get("stderr");
            Integer compileCode = (Integer) compile.get("code");

            if (compileCode != null && compileCode != 0) {
                return builder
                        .result("CE")
                        .errorMessage("컴파일 에러: " + compileStderr)
                        .build();
            }
        }

        // 실행 단계 확인
        Map<String, Object> run = (Map<String, Object>) response.get("run");
        if (run == null) {
            return builder
                    .result("ERROR")
                    .errorMessage("실행 결과가 없습니다")
                    .build();
        }

        String stdout = (String) run.get("stdout");
        String stderr = (String) run.get("stderr");
        Integer exitCode = (Integer) run.get("code");
        String signal = (String) run.get("signal");

        builder.actualOutput(stdout);

        // 시그널로 종료된 경우 (타임아웃 등)
        if (signal != null && !signal.isEmpty()) {
            if ("SIGKILL".equals(signal) || "SIGXCPU".equals(signal)) {
                return builder
                        .result("TLE")
                        .errorMessage("시간 초과")
                        .build();
            }
            return builder
                    .result("RE")
                    .errorMessage("런타임 에러: " + signal)
                    .build();
        }

        // 런타임 에러 확인
        if (exitCode != null && exitCode != 0) {
            return builder
                    .result("RE")
                    .errorMessage("런타임 에러 (exit code: " + exitCode + "): " + stderr)
                    .build();
        }

        // stderr에 내용이 있으면 런타임 에러
        if (stderr != null && !stderr.trim().isEmpty()) {
            return builder
                    .result("RE")
                    .errorMessage("런타임 에러: " + stderr)
                    .build();
        }

        // 정답 비교
        String expected = testCase.getExpectedOutput();
        String actual = stdout;

        if (expected != null && actual != null) {
            // 공백/개행 정규화 후 비교
            String normalizedExpected = expected.trim().replaceAll("\\r\\n", "\n").replaceAll("\\s+$", "");
            String normalizedActual = actual.trim().replaceAll("\\r\\n", "\n").replaceAll("\\s+$", "");

            if (normalizedExpected.equals(normalizedActual)) {
                return builder.result("AC").build();
            } else {
                return builder
                        .result("WA")
                        .errorMessage("출력이 예상 결과와 다릅니다")
                        .build();
            }
        }

        return builder.result("AC").build();
    }

    /**
     * 전체 결과 판정
     */
    private String determineOverallResult(List<TestRunResponseDto.TestCaseResultDto> results,
                                          int passedCount, int totalCount) {
        if (passedCount == totalCount) {
            return "AC";
        }

        boolean hasCompileError = results.stream().anyMatch(r -> "CE".equals(r.getResult()));
        if (hasCompileError) return "CE";

        boolean hasRuntimeError = results.stream().anyMatch(r -> "RE".equals(r.getResult()));
        if (hasRuntimeError) return "RE";

        boolean hasTimeLimit = results.stream().anyMatch(r -> "TLE".equals(r.getResult()));
        if (hasTimeLimit) return "TLE";

        boolean hasMemoryLimit = results.stream().anyMatch(r -> "MLE".equals(r.getResult()));
        if (hasMemoryLimit) return "MLE";

        return "WA";
    }
}
