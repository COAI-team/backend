package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.domain.AlgoProblem;
import kr.or.kosa.backend.algorithm.domain.AlgoSubmission;
import kr.or.kosa.backend.algorithm.dto.ScoreCalculationParams;
import kr.or.kosa.backend.algorithm.dto.ScoreCalculationResult;
import kr.or.kosa.backend.algorithm.mapper.AlgorithmSubmissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

/**
 * 알고리즘 평가 및 점수 계산 전담 서비스
 * - AI 코드 평가 요청
 * - 종합 점수 계산
 * - 제출 결과 업데이트
 *
 * 분리 이유: 평가 로직의 독립성과 재사용성 확보
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlgorithmEvaluationService {

    private final CodeEvaluationService codeEvaluationService;
    private final ScoreCalculator scoreCalculator;
    private final AlgorithmSubmissionMapper submissionMapper;

    /**
     * 제출에 대한 AI 평가 및 점수 계산 처리 (비동기)
     *
     * @param submissionId 제출 ID
     * @param problem 문제 정보
     * @param judgeResult Judge0 채점 결과
     */
    @Async
    @Transactional
    public CompletableFuture<Void> processEvaluationAsync(
            Long submissionId,
            AlgoProblem problem,
            Judge0Service.JudgeResultDto judgeResult) {

        return CompletableFuture.runAsync(() -> {
            log.info("AI 평가 및 점수 계산 시작 - submissionId: {}", submissionId);

            try {
                // 1. 현재 제출 정보 조회
                AlgoSubmission submission = submissionMapper.selectSubmissionById(submissionId);
                if (submission == null) {
                    throw new IllegalArgumentException("제출 정보를 찾을 수 없습니다: " + submissionId);
                }

                // 2. AI 코드 평가 요청
                CompletableFuture<AICodeEvaluationResult> aiFuture = codeEvaluationService.evaluateCode(
                        submission.getSourceCode(),
                        problem.getAlgoProblemDescription(),
                        submission.getLanguage().name(),
                        judgeResult.getOverallResult()
                );

                // 3. AI 평가 결과 대기
                AICodeEvaluationResult aiResult = aiFuture.get();

                // 4. 종합 점수 계산 파라미터 준비
                ScoreCalculationParams scoreParams = ScoreCalculationParams.builder()
                        .judgeResult(judgeResult.getOverallResult())
                        .passedTestCount(judgeResult.getPassedTestCount())
                        .totalTestCount(judgeResult.getTotalTestCount())
                        .aiScore(aiResult.getAiScore())
                        .solvingTimeSeconds(submission.getSolvingDurationSeconds())
                        .timeLimitSeconds(1800) // 30분 기본 제한시간
                        .difficulty(problem.getAlgoProblemDifficulty())
                        .build();

                // 5. 종합 점수 계산
                ScoreCalculationResult scoreResult = scoreCalculator.calculateFinalScore(scoreParams);

                // 6. 제출 정보 업데이트
                updateSubmissionWithEvaluation(submission, aiResult, scoreResult);
                submissionMapper.updateSubmission(submission);

                log.info("AI 평가 및 점수 계산 완료 - submissionId: {}, 최종점수: {}",
                        submissionId, scoreResult.getFinalScore());

            } catch (Exception e) {
                log.error("AI 평가 및 점수 계산 중 오류 발생 - submissionId: {}", submissionId, e);

                // 실패 시 기본 상태로 설정
                markEvaluationFailed(submissionId, e.getMessage());
            }
        });
    }

    /**
     * AI 평가 결과로 제출 정보 업데이트
     */
    private void updateSubmissionWithEvaluation(
            AlgoSubmission submission,
            AICodeEvaluationResult aiResult,
            ScoreCalculationResult scoreResult) {

        // AI 평가 결과 설정
        submission.setAiFeedback(aiResult.getFeedback());
        submission.setAiFeedbackStatus(AlgoSubmission.AiFeedbackStatus.COMPLETED);
        submission.setAiScore(BigDecimal.valueOf(aiResult.getAiScore()));

        // 점수 정보 설정
        submission.setTimeEfficiencyScore(BigDecimal.valueOf(scoreResult.getTimeEfficiencyScore()));
        submission.setFinalScore(BigDecimal.valueOf(scoreResult.getFinalScore()));

        // 점수 가중치 정보 JSON으로 저장
        submission.setScoreWeights(convertScoreWeightsToJson(scoreResult));
    }

    /**
     * 평가 실패 처리
     */
    private void markEvaluationFailed(Long submissionId, String errorMessage) {
        try {
            AlgoSubmission submission = submissionMapper.selectSubmissionById(submissionId);
            if (submission != null) {
                submission.setAiFeedbackStatus(AlgoSubmission.AiFeedbackStatus.FAILED);
                submission.setAiFeedback("AI 평가 실패: " + errorMessage);
                submission.setAiScore(BigDecimal.valueOf(50.0)); // 기본 점수
                submissionMapper.updateSubmission(submission);
            }
        } catch (Exception e) {
            log.error("평가 실패 처리 중 오류 발생 - submissionId: {}", submissionId, e);
        }
    }

    /**
     * 점수 가중치 정보를 JSON 문자열로 변환
     */
    private String convertScoreWeightsToJson(ScoreCalculationResult scoreResult) {
        try {
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
                    }""",
                    scoreResult.getJudgeScore(),
                    scoreResult.getAiScore(),
                    scoreResult.getTimeEfficiencyScore(),
                    scoreResult.getFinalScore(),
                    scoreResult.getScoreGrade());
        } catch (Exception e) {
            log.warn("점수 가중치 JSON 생성 실패", e);
            return "{}";
        }
    }

    /**
     * 특정 제출의 AI 평가 재실행
     */
    @Transactional
    public CompletableFuture<Void> retryEvaluation(Long submissionId) {
        log.info("AI 평가 재실행 - submissionId: {}", submissionId);

        return CompletableFuture.runAsync(() -> {
            try {
                AlgoSubmission submission = submissionMapper.selectSubmissionById(submissionId);
                if (submission == null) {
                    throw new IllegalArgumentException("제출 정보를 찾을 수 없습니다");
                }

                // 평가 상태를 PENDING으로 재설정
                submission.setAiFeedbackStatus(AlgoSubmission.AiFeedbackStatus.PENDING);
                submissionMapper.updateSubmission(submission);

                // Judge 결과를 기반으로 재평가 트리거
                // 실제 구현 시에는 알고리즘 문제 정보와 Judge 결과가 필요
                log.info("AI 평가 재실행 요청 처리됨 - submissionId: {}", submissionId);

            } catch (Exception e) {
                log.error("AI 평가 재실행 중 오류 발생 - submissionId: {}", submissionId, e);
                markEvaluationFailed(submissionId, "재실행 실패: " + e.getMessage());
            }
        });
    }

    /**
     * 제출의 현재 평가 상태 조회
     */
    @Transactional(readOnly = true)
    public EvaluationStatusDto getEvaluationStatus(Long submissionId) {
        AlgoSubmission submission = submissionMapper.selectSubmissionById(submissionId);
        if (submission == null) {
            throw new IllegalArgumentException("제출 정보를 찾을 수 없습니다");
        }

        return EvaluationStatusDto.builder()
                .submissionId(submissionId)
                .aiFeedbackStatus(submission.getAiFeedbackStatus() != null ?
                        submission.getAiFeedbackStatus().name() : "PENDING")
                .aiScore(submission.getAiScore())
                .finalScore(submission.getFinalScore())
                .hasAiFeedback(submission.getAiFeedback() != null && !submission.getAiFeedback().trim().isEmpty())
                .build();
    }

    /**
     * 평가 상태 DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class EvaluationStatusDto {
        private Long submissionId;
        private String aiFeedbackStatus;
        private BigDecimal aiScore;
        private BigDecimal finalScore;
        private Boolean hasAiFeedback;
    }
}