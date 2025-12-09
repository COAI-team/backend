package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.dto.AlgoProblemDto;
import kr.or.kosa.backend.algorithm.dto.AlgoTestcaseDto;
import kr.or.kosa.backend.algorithm.dto.ValidationResultDto;
import kr.or.kosa.backend.algorithm.dto.request.ProblemGenerationRequestDto;
import kr.or.kosa.backend.algorithm.dto.response.ProblemGenerationResponseDto;
import kr.or.kosa.backend.algorithm.service.validation.CodeExecutionValidator;
import kr.or.kosa.backend.algorithm.service.validation.SelfCorrectionService;
import kr.or.kosa.backend.algorithm.service.validation.SimilarityChecker;
import kr.or.kosa.backend.algorithm.service.validation.StructureValidator;
import kr.or.kosa.backend.algorithm.service.validation.TimeRatioValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Phase 5-1: 문제 생성 메인 오케스트레이터
 * LLM 문제 생성 → 검증 → Self-Correction → DB 저장 전체 플로우 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemGenerationOrchestrator {

    private final LLMChatService llmChatService;
    private final LLMResponseParser llmResponseParser;
    private final ProblemGenerationPromptBuilder promptBuilder;
    private final AlgorithmProblemService problemService;

    // 검증기들
    private final StructureValidator structureValidator;
    private final CodeExecutionValidator codeExecutionValidator;
    private final TimeRatioValidator timeRatioValidator;
    private final SimilarityChecker similarityChecker;
    private final SelfCorrectionService selfCorrectionService;

    /**
     * 문제 생성 전체 플로우 실행
     *
     * @param request          문제 생성 요청
     * @param userId           생성자 ID
     * @param progressCallback 진행률 콜백 (nullable)
     * @return 생성된 문제 응답
     */
    public ProblemGenerationResponseDto generateProblem(
            ProblemGenerationRequestDto request,
            Long userId,
            Consumer<ProgressEvent> progressCallback) {

        log.info("문제 생성 플로우 시작 - topic: {}, difficulty: {}",
                request.getTopic(), request.getDifficulty());

        try {
            // 1. LLM으로 문제 생성
            notifyProgress(progressCallback, "GENERATING", "LLM 문제 생성 중...", 10);
            ProblemGenerationResponseDto generatedProblem = generateWithLLM(request);

            if (generatedProblem == null || generatedProblem.getProblem() == null) {
                throw new RuntimeException("LLM 문제 생성 실패");
            }

            // 2. 검증 및 Self-Correction 루프
            int attempt = 0;
            List<ValidationResultDto> validationResults;

            do {
                attempt++;
                notifyProgress(progressCallback, "VALIDATING",
                        String.format("검증 중... (시도 %d/%d)", attempt, selfCorrectionService.getMaxAttempts() + 1),
                        20 + (attempt * 15));

                // 검증 실행
                validationResults = runAllValidations(generatedProblem);

                // 모든 검증 통과 확인
                boolean allPassed = validationResults.stream().allMatch(ValidationResultDto::isPassed);

                if (allPassed) {
                    log.info("모든 검증 통과 - 시도 #{}", attempt);
                    break;
                }

                // Self-Correction 시도
                if (selfCorrectionService.canAttemptMore(attempt)) {
                    notifyProgress(progressCallback, "CORRECTING",
                            String.format("Self-Correction 시도 %d...", attempt), 50 + (attempt * 10));

                    ProblemGenerationResponseDto corrected = selfCorrectionService.attemptCorrection(
                            generatedProblem.getProblem(),
                            generatedProblem.getTestCases(),
                            generatedProblem.getOptimalCode(),
                            generatedProblem.getNaiveCode(),
                            validationResults,
                            attempt);

                    if (corrected != null && corrected.getProblem() != null) {
                        generatedProblem = corrected;
                    }
                } else {
                    log.warn("최대 수정 시도 횟수 초과, 경고와 함께 저장 진행");
                    break;
                }

            } while (attempt <= selfCorrectionService.getMaxAttempts());

            // 3. 검증 결과 요약 추가
            generatedProblem.setValidationResults(validationResults);

            // 4. DB 저장
            notifyProgress(progressCallback, "SAVING", "문제 저장 중...", 90);
            Long problemId = problemService.saveGeneratedProblem(generatedProblem, userId);
            generatedProblem.setProblemId(problemId);

            notifyProgress(progressCallback, "COMPLETED", "문제 생성 완료", 100);
            log.info("문제 생성 완료 - problemId: {}", problemId);

            return generatedProblem;

        } catch (Exception e) {
            log.error("문제 생성 플로우 중 오류 발생", e);
            notifyProgress(progressCallback, "ERROR", "오류 발생: " + e.getMessage(), -1);
            throw new RuntimeException("문제 생성 실패: " + e.getMessage(), e);
        }
    }

    /**
     * LLM으로 문제 생성
     */
    private ProblemGenerationResponseDto generateWithLLM(ProblemGenerationRequestDto request) {
        log.info("LLM 문제 생성 시작");

        // 프롬프트 생성
        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt = promptBuilder.buildUserPromptWithoutRag(request);

        // LLM 호출
        String response = llmChatService.generate(systemPrompt, userPrompt);

        // 응답 파싱
        LLMResponseParser.ParsedResult parsed = llmResponseParser.parse(response, request);

        // ParsedResult -> ProblemGenerationResponseDto 변환
        return ProblemGenerationResponseDto.builder()
                .problem(parsed.problem())
                .testCases(parsed.testCases())
                .optimalCode(parsed.optimalCode())
                .naiveCode(parsed.naiveCode())
                .language("Python 3")  // 기본 언어
                .status(ProblemGenerationResponseDto.GenerationStatus.SUCCESS)
                .build();
    }

    /**
     * 모든 검증 실행
     */
    private List<ValidationResultDto> runAllValidations(ProblemGenerationResponseDto problem) {
        List<ValidationResultDto> results = new ArrayList<>();

        AlgoProblemDto problemDto = problem.getProblem();
        List<AlgoTestcaseDto> testCases = problem.getTestCases();
        String optimalCode = problem.getOptimalCode();
        String naiveCode = problem.getNaiveCode();
        String language = problem.getLanguage() != null ? problem.getLanguage() : "Python 3";

        // 1. 구조 검증
        log.info("구조 검증 실행");
        ValidationResultDto structureResult = structureValidator.validate(
                problemDto, testCases, optimalCode, naiveCode);
        results.add(structureResult);

        // 2. 유사도 검사
        log.info("유사도 검사 실행");
        ValidationResultDto similarityResult = similarityChecker.checkSimilarity(problemDto);
        results.add(similarityResult);

        // 3. 코드 실행 검증 (최적 코드가 있을 경우)
        if (optimalCode != null && !optimalCode.isBlank()) {
            log.info("코드 실행 검증 실행");
            ValidationResultDto executionResult = codeExecutionValidator.validate(
                    optimalCode, language, testCases,
                    problemDto.getTimelimit(), problemDto.getMemorylimit());
            results.add(executionResult);
        }

        // 4. 시간 비율 검증 (최적/비효율 코드 모두 있을 경우)
        if (optimalCode != null && !optimalCode.isBlank() &&
            naiveCode != null && !naiveCode.isBlank()) {
            log.info("시간 비율 검증 실행");
            ValidationResultDto timeRatioResult = timeRatioValidator.validate(
                    optimalCode, naiveCode, language, testCases,
                    problemDto.getTimelimit(), problemDto.getMemorylimit());
            results.add(timeRatioResult);
        }

        // 결과 요약 로그
        long passedCount = results.stream().filter(ValidationResultDto::isPassed).count();
        log.info("검증 완료 - 통과: {}/{}", passedCount, results.size());

        return results;
    }

    /**
     * 진행률 알림
     */
    private void notifyProgress(Consumer<ProgressEvent> callback, String status, String message, int percentage) {
        if (callback != null) {
            callback.accept(new ProgressEvent(status, message, percentage));
        }
        log.debug("Progress: [{}%] {} - {}", percentage, status, message);
    }

    /**
     * 진행률 이벤트 클래스
     */
    public static class ProgressEvent {
        private final String status;
        private final String message;
        private final int percentage;

        public ProgressEvent(String status, String message, int percentage) {
            this.status = status;
            this.message = message;
            this.percentage = percentage;
        }

        public String getStatus() { return status; }
        public String getMessage() { return message; }
        public int getPercentage() { return percentage; }
    }
}
