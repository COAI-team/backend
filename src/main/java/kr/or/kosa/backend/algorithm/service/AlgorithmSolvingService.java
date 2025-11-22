package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.domain.AlgoProblem;
import kr.or.kosa.backend.algorithm.domain.AlgoSubmission;
import kr.or.kosa.backend.algorithm.domain.AlgoTestcase;
import kr.or.kosa.backend.algorithm.dto.*;
import kr.or.kosa.backend.algorithm.mapper.AlgorithmProblemMapper;
import kr.or.kosa.backend.algorithm.mapper.AlgorithmSubmissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
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
 * 알고리즘 문제 풀이 서비스 (ALG-04, ALG-07) - 수정 버전
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlgorithmSolvingService {

    private final AlgorithmProblemMapper problemMapper;
    private final AlgorithmSubmissionMapper submissionMapper;
    private final Judge0Service judge0Service;

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
     * 코드 제출 및 채점 (ALG-07)
     */
    @Transactional
    public SubmissionResponseDto submitCode(SubmissionRequestDto request, Long userId) {
        log.info("코드 제출 - problemId: {}, userId: {}, language: {}",
                request.getProblemId(), userId, request.getLanguage());

        // 요청 데이터 검증
        request.validate();

        // 1. 문제 존재 확인
        AlgoProblem problem = problemMapper.selectProblemById(request.getProblemId());
        if (problem == null) {
            throw new IllegalArgumentException("존재하지 않는 문제입니다");
        }

        // 2. 제출 엔티티 생성 및 저장
        AlgoSubmission submission = createSubmission(request, userId, problem);
        submissionMapper.insertSubmission(submission);

        log.info("제출 저장 완료 - submissionId: {}", submission.getAlgosubmissionId());

        // 3. 비동기로 Judge0 채점 시작
        processJudgingAsync(submission.getAlgosubmissionId(), request, problem);

        // 4. 즉시 응답 반환 (PENDING 상태)
        return convertToSubmissionResponse(submission, problem, null);
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
     * 비동기 채점 처리
     */
    @Async
    protected void processJudgingAsync(Long submissionId, SubmissionRequestDto request, AlgoProblem problem) {
        log.info("비동기 채점 시작 - submissionId: {}", submissionId);

        try {
            // 1. 모든 테스트케이스 조회
            List<AlgoTestcase> testCases = problemMapper.selectTestCasesByProblemId(request.getProblemId());

            List<Judge0Service.TestCaseDto> testCaseDtos = testCases.stream()
                    .map(tc -> Judge0Service.TestCaseDto.builder()
                            .input(tc.getInputData())
                            .expectedOutput(tc.getExpectedOutput())
                            .build())
                    .collect(Collectors.toList());

            // 2. Judge0로 채점
            CompletableFuture<Judge0Service.JudgeResultDto> judgeFuture =
                    judge0Service.judgeCode(request.getSourceCode(), request.getLanguage(), testCaseDtos);

            Judge0Service.JudgeResultDto judgeResult = judgeFuture.get();

            // 3. 점수 계산
            BigDecimal finalScore = calculateFinalScore(judgeResult, request, problem);

            // 4. 제출 결과 업데이트
            AlgoSubmission submission = submissionMapper.selectSubmissionById(submissionId);
            updateSubmissionWithResult(submission, judgeResult, finalScore, request);
            submissionMapper.updateSubmission(submission);

            log.info("채점 완료 - submissionId: {}, result: {}, score: {}",
                    submissionId, judgeResult.getOverallResult(), finalScore);

        } catch (Exception e) {
            log.error("채점 중 오류 발생 - submissionId: {}", submissionId, e);

            // 에러 상태로 업데이트
            AlgoSubmission submission = submissionMapper.selectSubmissionById(submissionId);
            submission.setJudgeResult(AlgoSubmission.JudgeResult.PENDING);
            submissionMapper.updateSubmission(submission);
        }
    }

    /**
     * 제출 엔티티 생성
     */
    private AlgoSubmission createSubmission(SubmissionRequestDto request, Long userId, AlgoProblem problem) {
        LocalDateTime now = LocalDateTime.now();

        // 풀이 시간 계산
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
                .aiFeedbackType(request.getFeedbackType() != null ?
                        request.getFeedbackType() : AlgoSubmission.AiFeedbackType.COMPREHENSIVE)
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

    /**
     * 채점 결과로 제출 업데이트
     */
    private void updateSubmissionWithResult(AlgoSubmission submission,
                                            Judge0Service.JudgeResultDto judgeResult,
                                            BigDecimal finalScore,
                                            SubmissionRequestDto request) {
        submission.setJudgeResult(AlgoSubmission.JudgeResult.valueOf(judgeResult.getOverallResult()));
        submission.setExecutionTime(judgeResult.getMaxExecutionTime());
        submission.setMemoryUsage(judgeResult.getMaxMemoryUsage());
        submission.setPassedTestCount(judgeResult.getPassedTestCount());
        submission.setTotalTestCount(judgeResult.getTotalTestCount());
        submission.setFinalScore(finalScore);

        if (request.getEndTime() == null) {
            submission.setEndSolving(LocalDateTime.now());
            if (submission.getStartSolving() != null) {
                submission.setSolvingDurationSeconds(
                        (int) Duration.between(submission.getStartSolving(), submission.getEndSolving()).getSeconds());
            }
        }

        // 시간 효율성 점수 계산
        BigDecimal timeEfficiencyScore = calculateTimeEfficiencyScore(submission, judgeResult);
        submission.setTimeEfficiencyScore(timeEfficiencyScore);
    }

    /**
     * 최종 점수 계산
     */
    private BigDecimal calculateFinalScore(Judge0Service.JudgeResultDto judgeResult,
                                           SubmissionRequestDto request,
                                           AlgoProblem problem) {
        // Judge 결과 점수 (40%)
        BigDecimal judgeScore = BigDecimal.ZERO;
        if ("AC".equals(judgeResult.getOverallResult())) {
            judgeScore = new BigDecimal("100");
        } else if (judgeResult.getPassedTestCount() > 0) {
            // 부분 점수
            double partialScore = (double) judgeResult.getPassedTestCount() / judgeResult.getTotalTestCount() * 50;
            judgeScore = new BigDecimal(partialScore).setScale(2, RoundingMode.HALF_UP);
        }

        // 난이도별 가산점
        BigDecimal difficultyMultiplier = getDifficultyMultiplier(problem.getAlgoProblemDifficulty());

        // 기본 점수 계산 (AI 점수와 시간 효율성은 나중에 업데이트)
        BigDecimal finalScore = judgeScore.multiply(difficultyMultiplier)
                .multiply(new BigDecimal("0.7")) // 70% 가중치
                .setScale(2, RoundingMode.HALF_UP);

        return finalScore;
    }

    /**
     * 시간 효율성 점수 계산
     */
    private BigDecimal calculateTimeEfficiencyScore(AlgoSubmission submission, Judge0Service.JudgeResultDto judgeResult) {
        if (submission.getSolvingDurationSeconds() == null) {
            return new BigDecimal("50"); // 기본 점수
        }

        // 30분(1800초) 기준으로 계산
        int timeLimit = 1800; // 30분
        int actualTime = submission.getSolvingDurationSeconds();

        if (actualTime >= timeLimit) {
            return new BigDecimal("10"); // 최소 점수
        }

        double efficiency = (double) (timeLimit - actualTime) / timeLimit * 100;
        return new BigDecimal(efficiency).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 난이도별 가중치
     */
    private BigDecimal getDifficultyMultiplier(kr.or.kosa.backend.algorithm.domain.ProblemDifficulty difficulty) {
        switch (difficulty) {
            case BRONZE: return new BigDecimal("1.0");
            case SILVER: return new BigDecimal("1.2");
            case GOLD: return new BigDecimal("1.5");
            case PLATINUM: return new BigDecimal("2.0");
            default: return new BigDecimal("1.0");
        }
    }

    /**
     * 테스트케이스 DTO 변환
     */
    private List<ProblemSolveResponseDto.TestCaseDto> convertToTestCaseDtos(List<AlgoTestcase> testCases) {
        return testCases.stream()
                .map(tc -> ProblemSolveResponseDto.TestCaseDto.builder()
                        .input(tc.getInputData())
                        .expectedOutput(tc.getExpectedOutput())
                        .isSample(tc.getIsSample())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 이전 제출 정보 변환
     */
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

    /**
     * 제출 응답 DTO 변환
     */
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
                .testCaseResults(convertTestCaseResults(testCaseResults))
                .aiFeedback(submission.getAiFeedback())
                .aiFeedbackStatus(submission.getAiFeedbackStatus() != null ?
                        submission.getAiFeedbackStatus().name() : "PENDING")
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

    /**
     * 채점 상태 판정
     */
    private String determineJudgeStatus(AlgoSubmission submission) {
        if (submission.getJudgeResult() == null || submission.getJudgeResult() == AlgoSubmission.JudgeResult.PENDING) {
            return "PENDING";
        }
        return "COMPLETED";
    }

    /**
     * 테스트케이스 결과 변환
     */
    private List<SubmissionResponseDto.TestCaseResultDto> convertTestCaseResults(
            List<Judge0Service.TestCaseResultDto> testCaseResults) {
        if (testCaseResults == null) {
            return null;
        }

        return testCaseResults.stream()
                .map(tcr -> SubmissionResponseDto.TestCaseResultDto.builder()
                        .testCaseNumber(tcr.getTestCaseNumber())
                        .input(tcr.getInput())
                        .expectedOutput(tcr.getExpectedOutput())
                        .actualOutput(tcr.getActualOutput())
                        .result(tcr.getResult())
                        .executionTime(tcr.getExecutionTime())
                        .errorMessage(tcr.getErrorMessage())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 점수 세부사항 생성
     */
    private SubmissionResponseDto.ScoreBreakdownDto createScoreBreakdown(AlgoSubmission submission) {
        return SubmissionResponseDto.ScoreBreakdownDto.builder()
                .judgeScore(calculateJudgeScore(submission))
                .aiScore(submission.getAiScore() != null ? submission.getAiScore() : BigDecimal.ZERO)
                .timeScore(submission.getTimeEfficiencyScore() != null ?
                        submission.getTimeEfficiencyScore() : BigDecimal.ZERO)
                .focusScore(submission.getFocusScore() != null ? submission.getFocusScore() : BigDecimal.ZERO)
                .scoreWeights("Judge(40%) + AI(30%) + Time(20%) + Focus(10%)")
                .build();
    }

    /**
     * Judge 점수 계산
     */
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