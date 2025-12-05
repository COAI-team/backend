package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.dto.AlgoProblemDto;
import kr.or.kosa.backend.algorithm.dto.AlgoSubmissionDto;
import kr.or.kosa.backend.algorithm.dto.AlgoTestcaseDto;
import kr.or.kosa.backend.algorithm.dto.SubmissionRequestDto;
import kr.or.kosa.backend.algorithm.dto.enums.AiFeedbackStatus;
import kr.or.kosa.backend.algorithm.dto.enums.JudgeResult;
import kr.or.kosa.backend.algorithm.mapper.AlgorithmProblemMapper;
import kr.or.kosa.backend.algorithm.mapper.AlgorithmSubmissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlgorithmJudgingService {

    private final AlgorithmSubmissionMapper submissionMapper;
    private final AlgorithmProblemMapper problemMapper;
    private final Judge0Service judge0Service;
    private final AlgorithmEvaluationService evaluationService;
    private final LanguageConstantService languageConstantService; // ✅ 추가

    /**
     * 통합 채점 및 평가 프로세스 (비동기)
     * - Judge0 채점 후 즉시 AI 평가 시작
     */
    @Async("judgeExecutor") // ✅ 비동기 어노테이션 추가
    public void processCompleteJudgingFlow(Long submissionId, SubmissionRequestDto request, AlgoProblemDto problem) {
        log.info("🔄 [스레드: {}] 통합 채점 프로세스 시작 - submissionId: {}",
                Thread.currentThread().getName(), submissionId);

        try {
            // 1. 모든 테스트케이스 조회
            List<AlgoTestcaseDto> testCases = problemMapper.selectTestCasesByProblemId(request.getProblemId());

            List<Judge0Service.TestCaseDto> testCaseDtos = testCases.stream()
                    .map(tc -> Judge0Service.TestCaseDto.builder()
                            .input(tc.getInputData())
                            .expectedOutput(tc.getExpectedOutput())
                            .build())
                    .collect(Collectors.toList());

            // 2. 언어별 제한 시간/메모리 계산
            String dbLanguageName = request.getLanguage(); // DB 언어명 직접 사용 (예: "Python 3", "Java 17")

            int realTimeLimit = languageConstantService.calculateRealTimeLimit(
                    dbLanguageName, problem.getTimelimit());
            int realMemoryLimit = languageConstantService.calculateRealMemoryLimit(
                    dbLanguageName, problem.getMemorylimit());

            log.info("언어별 제한 적용 - 언어: {}, 시간: {}ms, 메모리: {}MB",
                    dbLanguageName, realTimeLimit, realMemoryLimit);

            // 3. Judge0 채점 실행 (제한 시간/메모리 전달)
            CompletableFuture<Judge0Service.JudgeResultDto> judgeFuture = judge0Service.judgeCode(
                    request.getSourceCode(), dbLanguageName, testCaseDtos, realTimeLimit, realMemoryLimit);

            Judge0Service.JudgeResultDto judgeResult = judgeFuture.get();

            // 3. Judge 결과만으로 기본 제출 정보 업데이트
            updateSubmissionWithJudgeResult(submissionId, judgeResult, request);

            log.info("Judge0 채점 완료 - submissionId: {}, result: {}",
                    submissionId, judgeResult.getOverallResult());

            // 4. AI 평가 및 점수 계산 비동기 시작 (분리된 서비스)
            log.info("🤖 AI 평가 서비스 호출 시작 - submissionId: {}, 현재 스레드: {}",
                    submissionId, Thread.currentThread().getName());
            try {
                evaluationService.processEvaluationAsync(submissionId, problem, judgeResult);
                log.info("✅ AI 평가 서비스 호출 완료 - submissionId: {}", submissionId);
            } catch (Exception aiEx) {
                log.error("❌ AI 평가 서비스 호출 실패 - submissionId: {}", submissionId, aiEx);
                throw aiEx; // 상위 catch 블록에서 처리하도록 재던짐
            }

        } catch (Exception e) {
            log.error("통합 채점 프로세스 중 오류 발생 - submissionId: {}", submissionId, e);
            markSubmissionFailed(submissionId, e.getMessage());
        }
    }

    /**
     * Judge 결과로만 제출 업데이트 (기본 점수)
     */
    private void updateSubmissionWithJudgeResult(Long submissionId, Judge0Service.JudgeResultDto judgeResult,
            SubmissionRequestDto request) {
        AlgoSubmissionDto submission = submissionMapper.selectSubmissionById(submissionId);
        if (submission == null)
            return;

        // Judge 결과 설정
        submission.setJudgeResult(JudgeResult.valueOf(judgeResult.getOverallResult()));
        submission.setExecutionTime(judgeResult.getMaxExecutionTime());
        submission.setMemoryUsage(judgeResult.getMaxMemoryUsage());
        submission.setPassedTestCount(judgeResult.getPassedTestCount());
        submission.setTotalTestCount(judgeResult.getTotalTestCount());

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

        submissionMapper.updateSubmission(submission);
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
    private BigDecimal calculateBasicScore(Judge0Service.JudgeResultDto judgeResult) {
        if ("AC".equals(judgeResult.getOverallResult())) {
            return new BigDecimal("100");
        }

        if (judgeResult.getPassedTestCount() > 0 && judgeResult.getTotalTestCount() > 0) {
            double partialScore = (double) judgeResult.getPassedTestCount() /
                    judgeResult.getTotalTestCount() * 100;
            return new BigDecimal(partialScore).setScale(2, RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO;
    }

    /**
     * ProgrammingLanguage Enum을 DB의 LANGUAGE_CONSTANTS 테이블의 LANGUAGE_NAME으로 매핑
     */
    // mapEnumToDbName 메서드 제거됨
    // 이제 request.getLanguage()가 DB 언어명을 직접 반환하므로 Enum 변환 불필요
}
