package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.dto.AlgoProblemDto;
import kr.or.kosa.backend.algorithm.dto.AlgoSubmissionDto;
import kr.or.kosa.backend.algorithm.dto.AICodeEvaluationResult;
import kr.or.kosa.backend.algorithm.dto.ScoreCalculationParams;
import kr.or.kosa.backend.algorithm.dto.ScoreCalculationResult;
import kr.or.kosa.backend.algorithm.dto.response.SubmissionAiStatusResponseDto;
import kr.or.kosa.backend.algorithm.dto.response.TestRunResponseDto;
import kr.or.kosa.backend.algorithm.dto.enums.AiFeedbackStatus;
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
     * AI 평가 및 점수 계산 처리 (비동기 진입점)
     * - @Async와 @Transactional을 분리하여 트랜잭션 안정성 확보
     */
    @Async("aiEvaluationExecutor")
    public CompletableFuture<Void> processEvaluationAsync(
            Long submissionId,
            AlgoProblemDto problem,
            TestRunResponseDto judgeResult
    ) {
        log.info("🤖 AI 평가 비동기 진입점 - submissionId: {}, thread: {}",
                submissionId, Thread.currentThread().getName());

        try {
            // 트랜잭션이 필요한 작업을 별도 메서드로 분리
            executeEvaluationWithTransaction(submissionId, problem, judgeResult);
        } catch (Exception e) {
            log.error("❌ AI 평가 중 오류 발생: submissionId={}", submissionId, e);
            // 실패 처리도 별도 트랜잭션으로 실행
            markEvaluationFailed(submissionId, e.getMessage());
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * AI 평가 및 점수 계산 실제 로직 (트랜잭션 적용)
     */
    @Transactional
    public void executeEvaluationWithTransaction(
            Long submissionId,
            AlgoProblemDto problem,
            TestRunResponseDto judgeResult
    ) throws Exception {
        log.info("📊 AI 평가 트랜잭션 시작 - submissionId: {}", submissionId);

        // 1. 제출 조회
        AlgoSubmissionDto submission = submissionMapper.selectSubmissionById(submissionId);
        if (submission == null) {
            throw new IllegalArgumentException("제출 정보를 찾을 수 없습니다: " + submissionId);
        }

        // 2. AI 평가 호출 (CompletableFuture)
        log.info("🔄 AI 코드 평가 서비스 호출 중...");
        CompletableFuture<AICodeEvaluationResult> aiFuture =
                codeEvaluationService.evaluateCode(
                        submission.getSourceCode(),
                        problem.getAlgoProblemDescription(),
                        submission.getLanguage(),
                        judgeResult.getOverallResult()
                );

        // 3. 평가 완료 대기
        AICodeEvaluationResult aiResult = aiFuture.get();
        log.info("✅ AI 코드 평가 완료 - aiScore: {}", aiResult.getAiScore());

        // 4. 점수 계산
        ScoreCalculationParams params = ScoreCalculationParams.builder()
                .judgeResult(judgeResult.getOverallResult())
                .passedTestCount(judgeResult.getPassedCount())
                .totalTestCount(judgeResult.getTotalCount())
                .aiScore(aiResult.getAiScore())
                .solvingTimeSeconds(submission.getSolvingDurationSeconds())
                .timeLimitSeconds(1800)
                .difficulty(problem.getAlgoProblemDifficulty())
                .build();

        ScoreCalculationResult score = scoreCalculator.calculateFinalScore(params);

        // 5. 제출 정보 업데이트
        applyEvaluation(submission, aiResult, score);
        submissionMapper.updateSubmission(submission);

        log.info("🎉 AI 평가 완료 - submissionId: {}, 최종점수: {}",
                submissionId, score.getFinalScore());
    }

    /** 제출 정보 업데이트 */
    private void applyEvaluation(
            AlgoSubmissionDto submission,
            AICodeEvaluationResult ai,
            ScoreCalculationResult score
    ) {
        submission.setAiFeedback(ai.getFeedback());
        submission.setAiFeedbackStatus(AiFeedbackStatus.COMPLETED);
        submission.setAiScore(BigDecimal.valueOf(ai.getAiScore()));

        submission.setTimeEfficiencyScore(BigDecimal.valueOf(score.getTimeEfficiencyScore()));
        submission.setFinalScore(BigDecimal.valueOf(score.getFinalScore()));
        submission.setScoreWeights(scoreWeightsJson(score));
    }

    /**
     * 평가 실패 처리 (별도 트랜잭션으로 실행)
     * - REQUIRES_NEW로 새 트랜잭션 시작하여 메인 트랜잭션 롤백과 관계없이 실패 상태 저장
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void markEvaluationFailed(Long submissionId, String msg) {
        log.warn("⚠️ AI 평가 실패 처리 시작 - submissionId: {}, msg: {}", submissionId, msg);
        try {
            AlgoSubmissionDto submission = submissionMapper.selectSubmissionById(submissionId);
            if (submission == null) {
                log.error("❌ 제출 정보를 찾을 수 없음 - submissionId: {}", submissionId);
                return;
            }

            submission.setAiFeedbackStatus(AiFeedbackStatus.FAILED);
            submission.setAiFeedback("AI 평가 실패: " + msg);
            submission.setAiScore(BigDecimal.valueOf(50.0));
            submissionMapper.updateSubmission(submission);

            log.info("✅ AI 평가 실패 상태 저장 완료 - submissionId: {}", submissionId);

        } catch (Exception e) {
            log.error("❌ AI 평가 실패 처리 중 오류 발생: submissionId={}", submissionId, e);
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
    public SubmissionAiStatusResponseDto getEvaluationStatus(Long submissionId) {
        AlgoSubmissionDto submission = submissionMapper.selectSubmissionById(submissionId);
        if (submission == null) {
            throw new IllegalArgumentException("제출 정보를 찾을 수 없습니다: " + submissionId);
        }

        return SubmissionAiStatusResponseDto.builder()
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

        AlgoSubmissionDto submission = submissionMapper.selectSubmissionById(submissionId);
        if (submission == null) {
            throw new IllegalArgumentException("제출 정보를 찾을 수 없습니다: " + submissionId);
        }

        submission.setAiFeedbackStatus(AiFeedbackStatus.PENDING);
        submission.setAiFeedback(null);
        submission.setAiScore(null);
        submissionMapper.updateSubmission(submission);

        return CompletableFuture.completedFuture(null);
    }

}
