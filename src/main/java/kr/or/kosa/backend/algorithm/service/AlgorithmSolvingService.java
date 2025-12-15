package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.dto.AlgoProblemDto;
import kr.or.kosa.backend.algorithm.dto.AlgoSubmissionDto;
import kr.or.kosa.backend.algorithm.dto.AlgoTestcaseDto;
import kr.or.kosa.backend.algorithm.dto.LanguageDto;
import kr.or.kosa.backend.algorithm.dto.enums.AiFeedbackStatus;
import kr.or.kosa.backend.algorithm.dto.enums.AiFeedbackType;
import kr.or.kosa.backend.algorithm.dto.enums.JudgeResult;
import kr.or.kosa.backend.algorithm.dto.enums.LanguageType;
import kr.or.kosa.backend.algorithm.dto.enums.ProblemType;
import kr.or.kosa.backend.algorithm.dto.enums.SolveMode;
import kr.or.kosa.backend.algorithm.dto.request.SubmissionRequestDto;
import kr.or.kosa.backend.algorithm.dto.request.TestRunRequestDto;
import kr.or.kosa.backend.algorithm.dto.response.ProblemSolveResponseDto;
import kr.or.kosa.backend.algorithm.dto.response.SubmissionResponseDto;
import kr.or.kosa.backend.algorithm.dto.response.TestRunResponseDto;
import kr.or.kosa.backend.algorithm.mapper.AlgorithmProblemMapper;
import kr.or.kosa.backend.algorithm.mapper.AlgorithmSubmissionMapper;
import kr.or.kosa.backend.algorithm.mapper.MonitoringMapper;
import kr.or.kosa.backend.algorithm.dto.MonitoringSessionDto;
import kr.or.kosa.backend.commons.pagination.PageRequest;
import kr.or.kosa.backend.commons.pagination.PageResponse;
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
    private final MonitoringMapper monitoringMapper;  // 모니터링 세션 데이터 조회용
    private final CodeExecutorService codeExecutorService;  // Judge0 또는 Piston 선택
    private final AlgorithmJudgingService judgingService;
    private final LanguageService languageService;  // 언어 정보 조회 (DB 기반)

    /**
     * 문제 풀이 시작 (ALG-04)
     */
    @Transactional(readOnly = true)
    public ProblemSolveResponseDto startProblemSolving(Long problemId, Long userId) {
        log.info("문제 풀이 시작 - problemId: {}, userId: {}", problemId, userId);

        // 1. 문제 정보 조회
        AlgoProblemDto problem = problemMapper.selectProblemById(problemId);
        if (problem == null) {
            throw new IllegalArgumentException("존재하지 않는 문제입니다");
        }

        // 2. 샘플 테스트케이스 조회 (is_sample = true)
        List<AlgoTestcaseDto> sampleTestCases = problemMapper.selectSampleTestCasesByProblemId(problemId);

        // 3. 이전 제출 정보 조회 (최고 점수)
        AlgoSubmissionDto previousSubmission = submissionMapper.selectBestSubmissionByUserAndProblem(userId, problemId);

        // 4. Eye Tracking 세션 ID 생성
        String sessionId = UUID.randomUUID().toString();

        // 5. 언어별 제한 정보 구성
        // 변경사항 (2025-12-13): languageId (INT)를 사용하여 언어 식별
        ProblemType problemType = problem.getProblemType();
        LanguageType languageType = (problemType == ProblemType.SQL)
                ? LanguageType.DB
                : LanguageType.GENERAL;

        List<LanguageDto> languages = languageService.getLanguagesByType(languageType);

        List<ProblemSolveResponseDto.LanguageOption> availableLanguages = languages.stream()
                .map(lang -> ProblemSolveResponseDto.LanguageOption.builder()
                        .languageId(lang.getLanguageId())  // Judge0 API ID (예: 100=Python, 91=Java)
                        .languageName(lang.getLanguageName())
                        .timeLimit(lang.calculateRealTimeLimit(problem.getTimelimit()))
                        .memoryLimit(lang.calculateRealMemoryLimit(problem.getMemorylimit()))
                        .build())
                .collect(Collectors.toList());

        return ProblemSolveResponseDto.builder()
                .problemId(problem.getAlgoProblemId())
                .title(problem.getAlgoProblemTitle())
                .description(problem.getAlgoProblemDescription())
                .difficulty(problem.getAlgoProblemDifficulty().name())
                .timeLimit(problem.getTimelimit())
                .memoryLimit(problem.getMemorylimit())
                .problemType(problemType != null ? problemType.name() : "ALGORITHM")
                .initScript(problem.getInitScript())
                .availableLanguages(availableLanguages)
                .sampleTestCases(convertToTestCaseDtos(sampleTestCases))
                .sessionStartTime(LocalDateTime.now())
                .sessionId(sessionId)
                .previousSubmission(convertToPreviousSubmission(previousSubmission))
                .build();
    }

    /**
     * 코드 제출 및 채점 (ALG-07) - 통합 플로우
     * 변경사항 (2025-12-13): language (String) → languageId (INT)
     */
    @Transactional
    public SubmissionResponseDto submitCode(SubmissionRequestDto request, Long userId) {
        log.info("코드 제출 시작 - problemId: {}, userId: {}, languageId: {}",
                request.getProblemId(), userId, request.getLanguageId());

        // 1. 요청 데이터 검증
        request.validate();

        // 2. 문제 존재 확인
        AlgoProblemDto problem = problemMapper.selectProblemById(request.getProblemId());
        if (problem == null) {
            throw new IllegalArgumentException("존재하지 않는 문제입니다");
        }

        // 3. 제출 엔티티 생성 및 저장
        AlgoSubmissionDto submission = createSubmission(request, userId, problem);
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
     *
     * 변경사항 (2025-12-13): language (String) → languageId (INT)
     */
    public TestRunResponseDto runSampleTest(TestRunRequestDto request) {
        log.info("샘플 테스트 실행 시작 - problemId: {}, languageId: {}",
                request.getProblemId(), request.getLanguageId());

        // 1. 문제 존재 확인
        AlgoProblemDto problem = problemMapper.selectProblemById(request.getProblemId());
        if (problem == null) {
            throw new IllegalArgumentException("존재하지 않는 문제입니다. ID: " + request.getProblemId());
        }

        // 2. 샘플 테스트케이스 조회 (isSample = true)
        List<AlgoTestcaseDto> sampleTestcases = problemMapper.selectSampleTestCasesByProblemId(request.getProblemId());

        if (sampleTestcases == null || sampleTestcases.isEmpty()) {
            throw new IllegalArgumentException("샘플 테스트케이스가 없습니다. 문제 ID: " + request.getProblemId());
        }

        log.info("샘플 테스트케이스 {} 개 조회됨", sampleTestcases.size());

        // 3. 언어 정보 조회 (languageId로 LANGUAGES 테이블 조회)
        Integer languageId = request.getLanguageId();
        LanguageDto language = languageService.getById(languageId);

        if (language == null) {
            throw new IllegalArgumentException(
                    "지원하지 않는 프로그래밍 언어입니다. languageId: " + languageId +
                    ". LANGUAGES 테이블에 등록된 언어를 사용해주세요.");
        }

        log.info("언어 정보 조회 완료 - languageId: {}, languageName: {}, timeFactor: {}, memoryFactor: {}",
                languageId, language.getLanguageName(), language.getTimeFactor(), language.getMemoryFactor());

        // 4. Judge0 또는 Piston 실행 (AlgoTestcaseDto 직접 전달)
        try {
            // 언어별 제한 시간/메모리 계산
            Integer realTimeLimit = language.calculateRealTimeLimit(problem.getTimelimit());
            Integer realMemoryLimit = language.calculateRealMemoryLimit(problem.getMemorylimit());

            log.info("코드 실행 제출 - languageId: {}, timeLimit: {}ms → {}ms, memoryLimit: {}MB → {}MB",
                    languageId, problem.getTimelimit(), realTimeLimit,
                    problem.getMemorylimit(), realMemoryLimit);

            // Judge0 또는 Piston 사용 (CodeExecutorService가 적절한 API 선택)
            CompletableFuture<TestRunResponseDto> judgeFuture = codeExecutorService
                    .judgeCode(request.getSourceCode(), languageId, sampleTestcases, realTimeLimit, realMemoryLimit);

            TestRunResponseDto judgeResult = judgeFuture.get();

            log.info("샘플 테스트 실행 완료 - 결과: {}, 통과: {}/{}",
                    judgeResult.getOverallResult(),
                    judgeResult.getPassedCount(),
                    judgeResult.getTotalCount());

            // 5. 응답 DTO 반환 (DB 저장 없이 바로 반환)
            return judgeResult;

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
     * 제출 결과 조회
     */
    @Transactional(readOnly = true)
    public SubmissionResponseDto getSubmissionResult(Long submissionId, Long userId) {
        log.info("제출 결과 조회 - submissionId: {}, userId: {}", submissionId, userId);

        AlgoSubmissionDto submission = submissionMapper.selectSubmissionById(submissionId);
        if (submission == null || !submission.getUserId().equals(userId)) {
            throw new IllegalArgumentException("해당 제출을 찾을 수 없습니다");
        }

        AlgoProblemDto problem = problemMapper.selectProblemById(submission.getAlgoProblemId());
        return convertToSubmissionResponse(submission, problem, null);
    }

    /**
     * 문제별 공유된 제출 목록 조회 (다른 사람의 풀이)
     */
    @Transactional(readOnly = true)
    public PageResponse<SubmissionResponseDto> getSharedSubmissions(Long problemId, int page, int size) {
        log.info("공유된 제출 목록 조회 - problemId: {}, page: {}, size: {}", problemId, page, size);

        // 1. 페이지 요청 객체 생성
        PageRequest pageRequest = new PageRequest(page, size);

        // 2. 총 개수 조회
        int totalCount = submissionMapper.countPublicSubmissionsByProblemId(problemId);

        // 3. 제출 목록 조회
        List<AlgoSubmissionDto> submissions = submissionMapper.selectPublicSubmissionsByProblemId(
                problemId,
                pageRequest.getOffset(),
                pageRequest.getSize()
        );

        // 4. DTO 변환
        List<SubmissionResponseDto> content = submissions.stream()
                .map(submission -> {
                    AlgoProblemDto problem = problemMapper.selectProblemById(submission.getAlgoProblemId());
                    return convertToSubmissionResponse(submission, problem, null);
                })
                .collect(Collectors.toList());

        // 5. PageResponse 반환
        return new PageResponse<>(content, pageRequest, totalCount);
    }

    /**
     * 공유 상태 업데이트 (ALG-09)
     */
    @Transactional
    public void updateSharingStatus(Long submissionId, Boolean isShared, Long userId) {
        log.info("공유 상태 업데이트 - submissionId: {}, isShared: {}, userId: {}",
                submissionId, isShared, userId);

        AlgoSubmissionDto submission = submissionMapper.selectSubmissionById(submissionId);
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
        List<AlgoSubmissionDto> submissions = submissionMapper.selectSubmissionsByUserId(userId, offset, size);

        return submissions.stream()
                .map(submission -> {
                    AlgoProblemDto problem = problemMapper.selectProblemById(submission.getAlgoProblemId());
                    return convertToSubmissionResponse(submission, problem, null);
                })
                .collect(Collectors.toList());
    }

    /**
     * 제출 DTO 생성
     * 변경사항 (2025-12-13): language (String) → languageId (INT)
     */
    private AlgoSubmissionDto createSubmission(SubmissionRequestDto request, Long userId, AlgoProblemDto problem) {
        LocalDateTime now = LocalDateTime.now();

        Integer solvingDuration = null;
        if (request.getStartTime() != null && request.getEndTime() != null) {
            solvingDuration = (int) Duration.between(request.getStartTime(), request.getEndTime()).getSeconds();
        }

        return AlgoSubmissionDto.builder()
                .algoProblemId(request.getProblemId())
                .userId(userId)
                .sourceCode(request.getSourceCode())
                .languageId(request.getLanguageId())  // languageId (INT) 사용
                .judgeResult(JudgeResult.PENDING)
                .aiFeedbackStatus(AiFeedbackStatus.PENDING)
                .aiFeedbackType(request.getFeedbackType() != null ? request.getFeedbackType()
                        : AiFeedbackType.COMPREHENSIVE)
                .startSolving(request.getStartTime())
                .endSolving(request.getEndTime())
                .solvingDurationSeconds(solvingDuration)
                // 풀이 모드 및 모니터링 세션 (focusSessionId, eyetracked 제거됨)
                .solveMode(request.getSolveMode() != null ? request.getSolveMode() : SolveMode.BASIC)
                .monitoringSessionId(request.getMonitoringSessionId())
                // GitHub 커밋 URL은 커밋 시 저장됨 (초기값 null)
                .githubCommitUrl(null)
                .isShared(false)
                .submittedAt(now)
                .build();
    }

    // DTO 변환 메소드들
    private List<ProblemSolveResponseDto.TestCaseDto> convertToTestCaseDtos(List<AlgoTestcaseDto> testCases) {
        return testCases.stream()
                .map(tc -> ProblemSolveResponseDto.TestCaseDto.builder()
                        .input(tc.getInputData())
                        .expectedOutput(tc.getExpectedOutput())
                        .isSample(tc.getIsSample())
                        .build())
                .collect(Collectors.toList());
    }

    private ProblemSolveResponseDto.SubmissionSummaryDto convertToPreviousSubmission(AlgoSubmissionDto submission) {
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

    private SubmissionResponseDto convertToSubmissionResponse(AlgoSubmissionDto submission,
            AlgoProblemDto problem,
            List<TestRunResponseDto.TestCaseResultDto> testCaseResults) {

        // 집중 모드일 경우 모니터링 통계 조회
        SubmissionResponseDto.MonitoringStatsDto monitoringStats = null;
        if (submission.getSolveMode() == SolveMode.FOCUS && submission.getMonitoringSessionId() != null) {
            monitoringStats = fetchMonitoringStats(submission.getMonitoringSessionId());
        }

        // 언어 정보 조회 (languageId → languageName 매핑)
        String languageName = null;
        if (submission.getLanguageId() != null) {
            LanguageDto language = languageService.getById(submission.getLanguageId());
            languageName = (language != null) ? language.getLanguageName() : "Unknown";
        }

        return SubmissionResponseDto.builder()
                .submissionId(submission.getAlgosubmissionId())
                .problemId(submission.getAlgoProblemId())
                .problemTitle(problem != null ? problem.getAlgoProblemTitle() : "Unknown")
                .problemDescription(problem != null ? problem.getAlgoProblemDescription() : null)
                .difficulty(problem != null && problem.getAlgoProblemDifficulty() != null
                        ? problem.getAlgoProblemDifficulty().name() : null)
                .timeLimit(problem != null ? problem.getTimelimit() : null)
                .memoryLimit(problem != null ? problem.getMemorylimit() : null)
                .languageId(submission.getLanguageId())
                .languageName(languageName)
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
                // focusScore 제거됨 - 모니터링은 점수에 미반영
                .solveMode(submission.getSolveMode() != null ? submission.getSolveMode().name() : "BASIC")
                .monitoringSessionId(submission.getMonitoringSessionId())
                .monitoringStats(monitoringStats)
                .timeEfficiencyScore(submission.getTimeEfficiencyScore())
                .finalScore(submission.getFinalScore())
                .scoreBreakdown(createScoreBreakdown(submission))
                .startTime(submission.getStartSolving())
                .endTime(submission.getEndSolving())
                .solvingDurationSeconds(submission.getSolvingDurationSeconds())
                .solvingDurationMinutes(submission.getSolvingDurationMinutes())
                .isShared(submission.getIsShared())
                .githubCommitUrl(submission.getGithubCommitUrl())
                .submittedAt(submission.getSubmittedAt())
                .build();
    }

    /**
     * 모니터링 세션에서 통계 데이터를 조회하여 DTO로 변환
     */
    private SubmissionResponseDto.MonitoringStatsDto fetchMonitoringStats(String sessionId) {
        try {
            MonitoringSessionDto session = monitoringMapper.findSessionById(sessionId);
            if (session == null) {
                log.warn("Monitoring session not found: {}", sessionId);
                return null;
            }

            return SubmissionResponseDto.MonitoringStatsDto.builder()
                    .fullscreenExitCount(session.getFullscreenExitCount())
                    .tabSwitchCount(session.getTabSwitchCount())
                    .mouseLeaveCount(session.getMouseLeaveCount())
                    .noFaceCount(session.getNoFaceCount())
                    .gazeAwayCount(session.getGazeAwayCount())
                    .totalViolations(session.getTotalViolations())
                    .warningShownCount(session.getWarningShownCount())
                    .autoSubmitted(Boolean.TRUE.equals(session.getAutoSubmitted()))
                    .sessionStatus(session.getSessionStatus() != null ? session.getSessionStatus().name() : null)
                    .build();
        } catch (Exception e) {
            log.error("Failed to fetch monitoring stats for session: {}", sessionId, e);
            return null;
        }
    }

    private String determineJudgeStatus(AlgoSubmissionDto submission) {
        if (submission.getJudgeResult() == null || submission.getJudgeResult() == JudgeResult.PENDING) {
            return "PENDING";
        }
        return "COMPLETED";
    }

    private SubmissionResponseDto.ScoreBreakdownDto createScoreBreakdown(AlgoSubmissionDto submission) {
        return SubmissionResponseDto.ScoreBreakdownDto.builder()
                .judgeScore(calculateJudgeScore(submission))
                .aiScore(submission.getAiScore() != null ? submission.getAiScore() : BigDecimal.ZERO)
                .timeScore(submission.getTimeEfficiencyScore() != null ? submission.getTimeEfficiencyScore()
                        : BigDecimal.ZERO)
                // focusScore 제거됨 - 모니터링은 점수에 미반영
                .scoreWeights("Judge(40%) + AI(30%) + Time(30%)")
                .build();
    }

    private BigDecimal calculateJudgeScore(AlgoSubmissionDto submission) {
        if (submission.getJudgeResult() == JudgeResult.AC) {
            return new BigDecimal("100");
        }

        if (submission.getPassedTestCount() != null && submission.getTotalTestCount() != null &&
                submission.getTotalTestCount() > 0) {
            double partialScore = (double) submission.getPassedTestCount() / submission.getTotalTestCount() * 100;
            return new BigDecimal(partialScore).setScale(2, RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO;
    }

    /**
     * ProgrammingLanguage Enum을 DB의 LANGUAGE_CONSTANTS 테이블의 LANGUAGE_NAME으로 매핑
     * (AlgorithmJudgingService와 동일한 로직)
     */
    // mapEnumToDbName 메서드 제거됨
    // 이제 request.getLanguage()가 DB 언어명을 직접 반환하므로 Enum 변환 불필요
}