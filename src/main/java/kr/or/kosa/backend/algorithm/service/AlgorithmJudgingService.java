package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.dto.AlgoProblemDto;
import kr.or.kosa.backend.algorithm.dto.AlgoSubmissionDto;
import kr.or.kosa.backend.algorithm.dto.AlgoTestcaseDto;
import kr.or.kosa.backend.algorithm.dto.request.SubmissionRequestDto;
import kr.or.kosa.backend.algorithm.dto.response.TestRunResponseDto;
import kr.or.kosa.backend.algorithm.dto.enums.AiFeedbackStatus;
import kr.or.kosa.backend.algorithm.dto.enums.JudgeResult;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 알고리즘 채점 서비스
 *
 * 변경사항 (2025-12-13):
 * - LanguageConstantService → LanguageService 교체
 * - language (String) → languageId (INT) 사용
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlgorithmJudgingService {

    private final AlgorithmSubmissionMapper submissionMapper;
    private final AlgorithmProblemMapper problemMapper;
    private final CodeExecutorService codeExecutorService;
    private final AlgorithmEvaluationService evaluationService;
    private final LanguageService languageService;  // 언어 정보 조회 (DB 기반)
    private final DailyQuizBonusService dailyQuizBonusService;

    /**
     * 통합 채점 및 평가 프로세스 (비동기)
     * - Judge0 채점 후 즉시 AI 평가 시작
     *
     * 변경사항 (2025-12-13): language (String) → languageId (INT)
     */
    @Async("judgeExecutor")
    public void processCompleteJudgingFlow(Long submissionId, SubmissionRequestDto request, AlgoProblemDto problem) {
        log.info("🔄 [스레드: {}] 통합 채점 프로세스 시작 - submissionId: {}",
                Thread.currentThread().getName(), submissionId);

        try {
            // 1. 모든 테스트케이스 조회
            List<AlgoTestcaseDto> testCases = problemMapper.selectTestCasesByProblemId(request.getProblemId());

            // 2. 언어별 제한 시간/메모리 계산 (languageId 사용)
            Integer languageId = request.getLanguageId();

            int realTimeLimit = languageService.calculateRealTimeLimit(
                    languageId, problem.getTimelimit());
            int realMemoryLimit = languageService.calculateRealMemoryLimit(
                    languageId, problem.getMemorylimit());

            log.info("언어별 제한 적용 - languageId: {}, 시간: {}ms, 메모리: {}MB",
                    languageId, realTimeLimit, realMemoryLimit);

            // 3. 코드 채점 실행 (Judge0 또는 Piston 사용)
            CompletableFuture<TestRunResponseDto> judgeFuture = codeExecutorService.judgeCode(
                    request.getSourceCode(), languageId, testCases, realTimeLimit, realMemoryLimit);

            TestRunResponseDto judgeResult = judgeFuture.get();

            // 4. Judge 결과만으로 기본 제출 정보 업데이트
            AlgoSubmissionDto updatedSubmission = updateSubmissionWithJudgeResult(submissionId, judgeResult, request);

            log.info("Judge0 채점 완료 - submissionId: {}, result: {}",
                    submissionId, judgeResult.getOverallResult());

            // 5. Daily Quiz 보너스 처리
            if (updatedSubmission != null && updatedSubmission.getJudgeResult() == JudgeResult.AC) {
                dailyQuizBonusService.handleDailyQuizSolved(
                        updatedSubmission.getUserId(),
                        updatedSubmission.getAlgoProblemId(),
                        LocalDate.now()
                );
            }

            // 6. AI 평가 및 점수 계산 비동기 시작 (분리된 서비스)
            log.info("🤖 AI 평가 서비스 호출 시작 - submissionId: {}, 현재 스레드: {}",
                    submissionId, Thread.currentThread().getName());
            try {
                evaluationService.processEvaluationAsync(submissionId, problem, judgeResult);
                log.info("✅ AI 평가 서비스 호출 완료 - submissionId: {}", submissionId);
            } catch (Exception aiEx) {
                log.error("❌ AI 평가 서비스 호출 실패 - submissionId: {}", submissionId, aiEx);
                throw aiEx;
            }

        } catch (Exception e) {
            log.error("통합 채점 프로세스 중 오류 발생 - submissionId: {}", submissionId, e);
            markSubmissionFailed(submissionId, e.getMessage());
        }
    }

    /**
     * Judge 결과로만 제출 업데이트 (기본 점수)
     */
    private AlgoSubmissionDto updateSubmissionWithJudgeResult(Long submissionId, TestRunResponseDto judgeResult,
                                                              SubmissionRequestDto request) {
        AlgoSubmissionDto submission = submissionMapper.selectSubmissionById(submissionId);
        if (submission == null)
            return null;

        // Judge 결과 설정
        submission.setJudgeResult(JudgeResult.valueOf(judgeResult.getOverallResult()));
        submission.setExecutionTime(judgeResult.getMaxExecutionTime());
        submission.setMemoryUsage(judgeResult.getMaxMemoryUsage());
        submission.setPassedTestCount(judgeResult.getPassedCount());
        submission.setTotalTestCount(judgeResult.getTotalCount());

        // 종료 시간 설정
        if (request.getEndTime() == null) {
            submission.setEndSolving(LocalDateTime.now());
            if (submission.getStartSolving() != null) {
                submission.setSolvingDurationSeconds(
                        (int) Duration.between(submission.getStartSolving(), submission.getEndSolving()).getSeconds());
            }
        }

        // 기본 점수 계산 (Judge 결과만으로)
        BigDecimal basicScore = calculateBasicScore(judgeResult);
        submission.setFinalScore(basicScore);
        // DB 업데이트
        submissionMapper.updateSubmission(submission);
        return submission;
    }

    /**
     * 제출 실패 표시
     */
    private void markSubmissionFailed(Long submissionId, String errorMessage) {
        try {
            AlgoSubmissionDto submission = submissionMapper.selectSubmissionById(submissionId);
            if (submission != null) {
                submission.setJudgeResult(JudgeResult.PENDING);
                submission.setAiFeedbackStatus(AiFeedbackStatus.FAILED);
                submissionMapper.updateSubmission(submission);
            }
        } catch (Exception e) {
            log.error("제출 실패 표시 중 오류 - submissionId: {}", submissionId, e);
        }
    }

    /**
     * 기본 점수 계산 (Judge 결과만 사용)
     */
    private BigDecimal calculateBasicScore(TestRunResponseDto judgeResult) {
        if ("AC".equals(judgeResult.getOverallResult())) {
            return new BigDecimal("100");
        }

        if (judgeResult.getPassedCount() > 0 && judgeResult.getTotalCount() > 0) {
            double partialScore = (double) judgeResult.getPassedCount() /
                    judgeResult.getTotalCount() * 100;
            return new BigDecimal(partialScore).setScale(2, RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO;
    }
}