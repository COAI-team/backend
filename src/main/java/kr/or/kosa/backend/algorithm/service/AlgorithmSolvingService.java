package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.domain.AlgoProblem;
import kr.or.kosa.backend.algorithm.domain.AlgoSubmission;
import kr.or.kosa.backend.algorithm.domain.AlgoTestcase;
import kr.or.kosa.backend.algorithm.domain.ProgrammingLanguage;
import kr.or.kosa.backend.algorithm.dto.*;
import kr.or.kosa.backend.algorithm.mapper.AlgorithmProblemMapper;
import kr.or.kosa.backend.algorithm.mapper.AlgorithmSubmissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 알고리즘 문제 풀이 핵심 서비스 (간소화 버전)
 * - 문제 풀이 시작 (ALG-04)
 * - 코드 제출 및 채점 (ALG-07)
 * - 제출 결과 조회
 * - 공유 상태 관리 (ALG-09)
 * - 사용자 제출 이력 (ALG-11)
 *
 * 분리된 기능: AI 평가 및 점수 계산 → AlgorithmEvaluationService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlgorithmSolvingService {

    private final AlgorithmProblemMapper problemMapper;
    private final AlgorithmSubmissionMapper submissionMapper;
    private final Judge0Service judge0Service;
    private final AlgorithmEvaluationService evaluationService; //
    private final AlgorithmJudgingService judgingService;

    /**
     * 문제 풀이 시작 (ALG-04)
     */
    @Transactional(readOnly = true)
    public ProblemSolveResponseDto startProblemSolving(Long problemId, Long userId) {
        log.info("문제 풀이 시작 - problemId: {}, userId: {}", problemId, userId);

        // 1. 문제 정보 조회
        AlgoProblem problem = problemMapper.selectProblemById(problemId);
        if (problem == null) {
            throw new IllegalArgumentException("존재하지 않는 문제입니다");
        }

        // 2. 샘플 테스트케이스 조회 (is_sample = true)
        List<AlgoTestcase> sampleTestCases = problemMapper.selectSampleTestCasesByProblemId(problemId);

        // 3. 이전 제출 정보 조회 (최고 점수)
        AlgoSubmission previousSubmission = submissionMapper.selectBestSubmissionByUserAndProblem(userId, problemId);

        // 4. Eye Tracking 세션 ID 생성
        String sessionId = UUID.randomUUID().toString();

        return ProblemSolveResponseDto.builder()
                .problemId(problem.getAlgoProblemId())
                .title(problem.getAlgoProblemTitle())
                .description(problem.getAlgoProblemDescription())
                .difficulty(problem.getAlgoProblemDifficulty().name())
                .timeLimit(problem.getTimelimit())
                .memoryLimit(problem.getMemorylimit())
                .sampleTestCases(convertToTestCaseDtos(sampleTestCases))
                .sessionStartTime(LocalDateTime.now())
                .sessionId(sessionId)
                .previousSubmission(convertToPreviousSubmission(previousSubmission))
                .build();
    }

    /**
     * 코드 제출 및 채점 (ALG-07) - 통합 플로우
     */
    @Transactional
    public SubmissionResponseDto submitCode(SubmissionRequestDto request, Long userId) {
        log.info("코드 제출 시작 - problemId: {}, userId: {}, language: {}",
                request.getProblemId(), userId, request.getLanguage());

        // 1. 요청 데이터 검증
        request.validate();

        // 2. 문제 존재 확인
        AlgoProblem problem = problemMapper.selectProblemById(request.getProblemId());
        if (problem == null) {
            throw new IllegalArgumentException("존재하지 않는 문제입니다");
        }

        // 3. 제출 엔티티 생성 및 저장
        AlgoSubmission submission = createSubmission(request, userId, problem);
        submissionMapper.insertSubmission(submission);

        log.info("제출 저장 완료 - submissionId: {}", submission.getAlgosubmissionId());

        // 4. 비동기로 Judge0 채점 및 AI 평가 프로세스 시작
        log.info("🚀 비동기 통합 프로세스 호출 시작 - submissionId: {}", submission.getAlgosubmissionId());
        judgingService.processCompleteJudgingFlow(submission.getAlgosubmissionId(), request, problem);
        log.info("✅ 비동기 통합 프로세스 호출 완료 - submissionId: {}", submission.getAlgosubmissionId());

        // 5. 즉시 응답 반환 (PENDING 상태)
        return convertToSubmissionResponse(submission, problem, null);
    }

    /**
     * 샘플 테스트케이스 실행 (제출 없이 코드 실행만)
     * - DB 저장 없음
     * - AI 평가 없음
     * - 샘플 테스트케이스(isSample=true)만 실행
     */
    public TestRunResponseDto runSampleTest(TestRunRequestDto request) {
        log.info("샘플 테스트 실행 시작 - problemId: {}, language: {}",
                request.getProblemId(), request.getLanguage());

        // 1. 문제 존재 확인
        AlgoProblem problem = problemMapper.selectProblemById(request.getProblemId());
        if (problem == null) {
            throw new IllegalArgumentException("존재하지 않는 문제입니다. ID: " + request.getProblemId());
        }

        // 2. 샘플 테스트케이스 조회 (isSample = true)
        List<AlgoTestcase> sampleTestcases = problemMapper.selectSampleTestCasesByProblemId(request.getProblemId());

        if (sampleTestcases == null || sampleTestcases.isEmpty()) {
            throw new IllegalArgumentException("샘플 테스트케이스가 없습니다. 문제 ID: " + request.getProblemId());
        }

        log.info("샘플 테스트케이스 {} 개 조회됨", sampleTestcases.size());

        // 3. 언어 문자열을 ProgrammingLanguage enum으로 변환
        ProgrammingLanguage language;
        try {
            language = ProgrammingLanguage.valueOf(request.getLanguage().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("지원하지 않는 프로그래밍 언어입니다: " + request.getLanguage());
        }

        // 4. Judge0 DTO로 변환
        List<Judge0Service.TestCaseDto> testCaseDtos = sampleTestcases.stream()
                .map(tc -> Judge0Service.TestCaseDto.builder()
                        .input(tc.getInputData())
                        .expectedOutput(tc.getExpectedOutput())
                        .build())
                .collect(Collectors.toList());

        // 5. Judge0 실행
        try {
            CompletableFuture<Judge0Service.JudgeResultDto> judgeFuture = judge0Service
                    .judgeCode(request.getSourceCode(), language, testCaseDtos);

            Judge0Service.JudgeResultDto judgeResult = judgeFuture.get();

            log.info("샘플 테스트 실행 완료 - 결과: {}, 통과: {}/{}",
                    judgeResult.getOverallResult(),
                    judgeResult.getPassedTestCount(),
                    judgeResult.getTotalTestCount());

            // 6. 응답 DTO 변환 (DB 저장 없이 바로 반환)
            return convertToTestRunResponse(judgeResult);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("샘플 테스트 실행 중 인터럽트 발생", e);
            throw new RuntimeException("테스트 실행이 중단되었습니다.", e);
        } catch (Exception e) {
            log.error("샘플 테스트 실행 중 오류 발생", e);
            throw new RuntimeException("테스트 실행 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * Judge0 결과를 TestRunResponseDto로 변환
     */
    private TestRunResponseDto convertToTestRunResponse(Judge0Service.JudgeResultDto judgeResult) {
        List<TestRunResponseDto.TestCaseResultDto> testCaseResults = null;

        if (judgeResult.getTestCaseResults() != null) {
            testCaseResults = judgeResult.getTestCaseResults().stream()
                    .map(r -> TestRunResponseDto.TestCaseResultDto.builder()
                            .testCaseNumber(r.getTestCaseNumber())
                            .input(r.getInput())
                            .expectedOutput(r.getExpectedOutput())
                            .actualOutput(r.getActualOutput())
                            .result(r.getResult())
                            .executionTime(r.getExecutionTime())
                            .memoryUsage(r.getMemoryUsage())
                            .errorMessage(r.getErrorMessage())
                            .build())
                    .collect(Collectors.toList());
        }

        return TestRunResponseDto.builder()
                .overallResult(judgeResult.getOverallResult())
                .passedCount(judgeResult.getPassedTestCount())
                .totalCount(judgeResult.getTotalTestCount())
                .testPassRate(judgeResult.getTestPassRate())
                .maxExecutionTime(judgeResult.getMaxExecutionTime())
                .maxMemoryUsage(judgeResult.getMaxMemoryUsage())
                .testCaseResults(testCaseResults)
                .build();
    }

    /**
     * 제출 결과 조회
     */
    @Transactional(readOnly = true)
    public SubmissionResponseDto getSubmissionResult(Long submissionId, Long userId) {
        log.info("제출 결과 조회 - submissionId: {}, userId: {}", submissionId, userId);

        AlgoSubmission submission = submissionMapper.selectSubmissionById(submissionId);
        if (submission == null || !submission.getUserId().equals(userId)) {
            throw new IllegalArgumentException("해당 제출을 찾을 수 없습니다");
        }

        AlgoProblem problem = problemMapper.selectProblemById(submission.getAlgoProblemId());
        return convertToSubmissionResponse(submission, problem, null);
    }

    /**
     * 공유 상태 업데이트 (ALG-09)
     */
    @Transactional
    public void updateSharingStatus(Long submissionId, Boolean isShared, Long userId) {
        log.info("공유 상태 업데이트 - submissionId: {}, isShared: {}, userId: {}",
                submissionId, isShared, userId);

        AlgoSubmission submission = submissionMapper.selectSubmissionById(submissionId);
        if (submission == null || !submission.getUserId().equals(userId)) {
            throw new IllegalArgumentException("해당 제출을 찾을 수 없습니다");
        }

        int updated = submissionMapper.updateSharingStatus(submissionId, isShared);
        if (updated == 0) {
            throw new RuntimeException("공유 상태 업데이트에 실패했습니다");
        }
    }

    /**
     * 사용자 제출 이력 조회 (ALG-11)
     */
    @Transactional(readOnly = true)
    public List<SubmissionResponseDto> getUserSubmissions(Long userId, int page, int size) {
        log.info("사용자 제출 이력 조회 - userId: {}, page: {}, size: {}", userId, page, size);

        int offset = page * size;
        List<AlgoSubmission> submissions = submissionMapper.selectSubmissionsByUserId(userId, offset, size);

        return submissions.stream()
                .map(submission -> {
                    AlgoProblem problem = problemMapper.selectProblemById(submission.getAlgoProblemId());
                    return convertToSubmissionResponse(submission, problem, null);
                })
                .collect(Collectors.toList());
    }

    /**
     * 제출 엔티티 생성
     */
    private AlgoSubmission createSubmission(SubmissionRequestDto request, Long userId, AlgoProblem problem) {
        LocalDateTime now = LocalDateTime.now();

        Integer solvingDuration = null;
        if (request.getStartTime() != null && request.getEndTime() != null) {
            solvingDuration = (int) Duration.between(request.getStartTime(), request.getEndTime()).getSeconds();
        }

        return AlgoSubmission.builder()
                .algoProblemId(request.getProblemId())
                .userId(userId)
                .sourceCode(request.getSourceCode())
                .language(request.getLanguage())
                .judgeResult(AlgoSubmission.JudgeResult.PENDING)
                .aiFeedbackStatus(AlgoSubmission.AiFeedbackStatus.PENDING)
                .aiFeedbackType(request.getFeedbackType() != null ? request.getFeedbackType()
                        : AlgoSubmission.AiFeedbackType.COMPREHENSIVE)
                .startSolving(request.getStartTime())
                .endSolving(request.getEndTime())
                .solvingDurationSeconds(solvingDuration)
                .focusSessionId(request.getFocusSessionId())
                .eyetracked(request.getFocusSessionId() != null)
                .githubCommitRequested(request.getRequestGithubCommit() != null && request.getRequestGithubCommit())
                .githubCommitStatus(AlgoSubmission.GithubCommitStatus.NONE)
                .isShared(false)
                .submittedAt(now)
                .build();
    }

    // DTO 변환 메소드들은 기존과 동일하게 유지
    private List<ProblemSolveResponseDto.TestCaseDto> convertToTestCaseDtos(List<AlgoTestcase> testCases) {
        return testCases.stream()
                .map(tc -> ProblemSolveResponseDto.TestCaseDto.builder()
                        .input(tc.getInputData())
                        .expectedOutput(tc.getExpectedOutput())
                        .isSample(tc.getIsSample())
                        .build())
                .collect(Collectors.toList());
    }

    private ProblemSolveResponseDto.SubmissionSummaryDto convertToPreviousSubmission(AlgoSubmission submission) {
        if (submission == null) {
            return null;
        }

        return ProblemSolveResponseDto.SubmissionSummaryDto.builder()
                .submissionId(submission.getAlgosubmissionId())
                .judgeResult(submission.getJudgeResult() != null ? submission.getJudgeResult().name() : "PENDING")
                .finalScore(submission.getFinalScore())
                .submittedAt(submission.getSubmittedAt())
                .build();
    }

    private SubmissionResponseDto convertToSubmissionResponse(AlgoSubmission submission,
            AlgoProblem problem,
            List<Judge0Service.TestCaseResultDto> testCaseResults) {
        return SubmissionResponseDto.builder()
                .submissionId(submission.getAlgosubmissionId())
                .problemId(submission.getAlgoProblemId())
                .problemTitle(problem != null ? problem.getAlgoProblemTitle() : "Unknown")
                .language(submission.getLanguage())
                .sourceCode(submission.getSourceCode())
                .judgeResult(submission.getJudgeResult() != null ? submission.getJudgeResult().name() : "PENDING")
                .judgeStatus(determineJudgeStatus(submission))
                .executionTime(submission.getExecutionTime())
                .memoryUsage(submission.getMemoryUsage())
                .passedTestCount(submission.getPassedTestCount())
                .totalTestCount(submission.getTotalTestCount())
                .testPassRate(submission.getTestPassRate())
                .aiFeedback(submission.getAiFeedback())
                .aiFeedbackStatus(
                        submission.getAiFeedbackStatus() != null ? submission.getAiFeedbackStatus().name() : "PENDING")
                .aiScore(submission.getAiScore())
                .focusScore(submission.getFocusScore())
                .timeEfficiencyScore(submission.getTimeEfficiencyScore())
                .finalScore(submission.getFinalScore())
                .scoreBreakdown(createScoreBreakdown(submission))
                .startTime(submission.getStartSolving())
                .endTime(submission.getEndSolving())
                .solvingDurationSeconds(submission.getSolvingDurationSeconds())
                .solvingDurationMinutes(submission.getSolvingDurationMinutes())
                .isShared(submission.getIsShared())
                .submittedAt(submission.getSubmittedAt())
                .build();
    }

    private String determineJudgeStatus(AlgoSubmission submission) {
        if (submission.getJudgeResult() == null || submission.getJudgeResult() == AlgoSubmission.JudgeResult.PENDING) {
            return "PENDING";
        }
        return "COMPLETED";
    }

    private SubmissionResponseDto.ScoreBreakdownDto createScoreBreakdown(AlgoSubmission submission) {
        return SubmissionResponseDto.ScoreBreakdownDto.builder()
                .judgeScore(calculateJudgeScore(submission))
                .aiScore(submission.getAiScore() != null ? submission.getAiScore() : BigDecimal.ZERO)
                .timeScore(submission.getTimeEfficiencyScore() != null ? submission.getTimeEfficiencyScore()
                        : BigDecimal.ZERO)
                .focusScore(submission.getFocusScore() != null ? submission.getFocusScore() : BigDecimal.ZERO)
                .scoreWeights("Judge(40%) + AI(30%) + Time(30%)")
                .build();
    }

    private BigDecimal calculateJudgeScore(AlgoSubmission submission) {
        if (submission.getJudgeResult() == AlgoSubmission.JudgeResult.AC) {
            return new BigDecimal("100");
        }

        if (submission.getPassedTestCount() != null && submission.getTotalTestCount() != null &&
                submission.getTotalTestCount() > 0) {
            double partialScore = (double) submission.getPassedTestCount() / submission.getTotalTestCount() * 100;
            return new BigDecimal(partialScore).setScale(2, RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO;
    }
}