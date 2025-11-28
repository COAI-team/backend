package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.domain.AlgoProblem;
import kr.or.kosa.backend.algorithm.domain.AlgoSubmission;
import kr.or.kosa.backend.algorithm.dto.AICodeEvaluationResult;
import kr.or.kosa.backend.algorithm.dto.ScoreCalculationParams;
import kr.or.kosa.backend.algorithm.dto.ScoreCalculationResult;
import kr.or.kosa.backend.algorithm.dto.SubmissionAiStatusDto;
import kr.or.kosa.backend.algorithm.mapper.AlgorithmSubmissionMapper;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlgorithmEvaluationService {

    private final CodeEvaluationService codeEvaluationService;
    private final ScoreCalculator scoreCalculator;
    private final AlgorithmSubmissionMapper submissionMapper;

    /**
     * AI 평가 및 점수 계산 처리
     */
    @Async("aiEvaluationExecutor")
    @Transactional
    public CompletableFuture<Void> processEvaluationAsync(
            Long submissionId,
            AlgoProblem problem,
            Judge0Service.JudgeResultDto judgeResult
    ) {

        log.info("AI 평가 및 점수 계산 시작 - submissionId: {}, thread: {}",
                submissionId, Thread.currentThread().getName());

        try {
            // 1. 제출 조회
            AlgoSubmission submission = submissionMapper.selectSubmissionById(submissionId);
            if (submission == null) {
                throw new IllegalArgumentException("제출 정보를 찾을 수 없습니다: " + submissionId);
            }

            // 2. AI 평가 호출 (CompletableFuture)
            CompletableFuture<AICodeEvaluationResult> aiFuture =
                    codeEvaluationService.evaluateCode(
                            submission.getSourceCode(),
                            problem.getAlgoProblemDescription(),
                            submission.getLanguage().name(),
                            judgeResult.getOverallResult()
                    );

            // 3. 평가 완료 대기
            AICodeEvaluationResult aiResult = aiFuture.get();

            // 4. 점수 계산
            ScoreCalculationParams params = ScoreCalculationParams.builder()
                    .judgeResult(judgeResult.getOverallResult())
                    .passedTestCount(judgeResult.getPassedTestCount())
                    .totalTestCount(judgeResult.getTotalTestCount())
                    .aiScore(aiResult.getAiScore())
                    .solvingTimeSeconds(submission.getSolvingDurationSeconds())
                    .timeLimitSeconds(1800)
                    .difficulty(problem.getAlgoProblemDifficulty())
                    .build();

            ScoreCalculationResult score = scoreCalculator.calculateFinalScore(params);

            // 5. 제출 정보 업데이트
            applyEvaluation(submission, aiResult, score);
            submissionMapper.updateSubmission(submission);

            log.info("AI 평가 완료 - submissionId: {}, 최종점수: {}",
                    submissionId, score.getFinalScore());

        } catch (Exception e) {
            log.error("AI 평가 중 오류 발생: submissionId={}", submissionId, e);
            markEvaluationFailed(submissionId, e.getMessage());
        }

        return CompletableFuture.completedFuture(null);
    }

    /** 제출 정보 업데이트 */
    private void applyEvaluation(
            AlgoSubmission submission,
            AICodeEvaluationResult ai,
            ScoreCalculationResult score
    ) {
        submission.setAiFeedback(ai.getFeedback());
        submission.setAiFeedbackStatus(AlgoSubmission.AiFeedbackStatus.COMPLETED);
        submission.setAiScore(BigDecimal.valueOf(ai.getAiScore()));

        submission.setTimeEfficiencyScore(BigDecimal.valueOf(score.getTimeEfficiencyScore()));
        submission.setFinalScore(BigDecimal.valueOf(score.getFinalScore()));
        submission.setScoreWeights(scoreWeightsJson(score));
    }

    /** 평가 실패 처리 */
    private void markEvaluationFailed(Long submissionId, String msg) {
        try {
            AlgoSubmission submission = submissionMapper.selectSubmissionById(submissionId);
            if (submission == null) return;

            submission.setAiFeedbackStatus(AlgoSubmission.AiFeedbackStatus.FAILED);
            submission.setAiFeedback("AI 평가 실패: " + msg);
            submission.setAiScore(BigDecimal.valueOf(50.0));
            submissionMapper.updateSubmission(submission);

        } catch (Exception e) {
            log.error("AI 평가 실패 처리 중 오류 발생: {}", submissionId, e);
        }
    }

    /** 점수 가중치 JSON 생성 */
    private String scoreWeightsJson(ScoreCalculationResult r) {
        return String.format("""
                {
                    "judgeScore": %.2f,
                    "judgeWeight": 40,
                    "aiScore": %.2f,
                    "aiWeight": 30,
                    "timeScore": %.2f,
                    "timeWeight": 30,
                    "finalScore": %.2f,
                    "grade": "%s"
                }
                """,
                r.getJudgeScore(),
                r.getAiScore(),
                r.getTimeEfficiencyScore(),
                r.getFinalScore(),
                r.getScoreGrade()
        );
    }

    @Transactional(readOnly = true)
    public SubmissionAiStatusDto getEvaluationStatus(Long submissionId) {
        AlgoSubmission submission = submissionMapper.selectSubmissionById(submissionId);
        if (submission == null) {
            throw new IllegalArgumentException("제출 정보를 찾을 수 없습니다: " + submissionId);
        }

        return SubmissionAiStatusDto.builder()
                .submissionId(submissionId)
                .aiFeedbackStatus(
                        submission.getAiFeedbackStatus() != null
                                ? submission.getAiFeedbackStatus().name()
                                : "PENDING"
                )
                .aiScore(submission.getAiScore())
                .finalScore(submission.getFinalScore())
                .hasAiFeedback(submission.getAiFeedback() != null)
                .build();
    }

    @Async("aiEvaluationExecutor")
    @Transactional
    public CompletableFuture<Void> retryEvaluation(Long submissionId) {

        AlgoSubmission submission = submissionMapper.selectSubmissionById(submissionId);
        if (submission == null) {
            throw new IllegalArgumentException("제출 정보를 찾을 수 없습니다: " + submissionId);
        }

        submission.setAiFeedbackStatus(AlgoSubmission.AiFeedbackStatus.PENDING);
        submission.setAiFeedback(null);
        submission.setAiScore(null);
        submissionMapper.updateSubmission(submission);

        return CompletableFuture.completedFuture(null);
    }

}
