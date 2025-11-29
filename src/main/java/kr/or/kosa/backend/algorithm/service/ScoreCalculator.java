package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.dto.ScoreCalculationParams;
import kr.or.kosa.backend.algorithm.dto.ScoreCalculationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 종합 점수 계산 시스템 (ALG-08)
 * Judge 결과, AI 점수, 시간 효율성을 종합하여 최종 점수 산출
 */
@Slf4j
@Component
public class ScoreCalculator {

    // 점수 가중치 (총 100%)
    private static final double JUDGE_WEIGHT = 0.40; // Judge0 결과 40%
    private static final double AI_WEIGHT = 0.30; // AI 코드 품질 30%
    private static final double TIME_EFFICIENCY_WEIGHT = 0.30; // 시간 효율성 30%

    /**
     * 최종 종합 점수 계산
     *
     * @param params 점수 계산에 필요한 파라미터들
     * @return 계산된 점수 정보
     */
    public ScoreCalculationResult calculateFinalScore(ScoreCalculationParams params) {
        log.info("점수 계산 시작 - Judge: {}, AI: {}, 시간: {}초",
                params.getJudgeResult(), params.getAiScore(), params.getSolvingTimeSeconds());

        try {
            // 1. Judge 점수 계산 (40%)
            double judgeScore = calculateJudgeScore(
                    params.getJudgeResult(),
                    params.getPassedTestCount(),
                    params.getTotalTestCount());

            // 2. AI 코드 품질 점수 (30%) - 이미 0-100 점수
            double aiScore = params.getAiScore() != null ? params.getAiScore() : 0.0;

            // 3. 시간 효율성 점수 계산 (30%)
            double timeEfficiencyScore = calculateTimeEfficiencyScore(
                    params.getSolvingTimeSeconds(),
                    params.getTimeLimitSeconds());

            // 4. 최종 점수 계산 (가중 평균)
            double finalScore = (judgeScore * JUDGE_WEIGHT) +
                    (aiScore * AI_WEIGHT) +
                    (timeEfficiencyScore * TIME_EFFICIENCY_WEIGHT);

            // 5. 소수점 둘째 자리까지 반올림
            finalScore = BigDecimal.valueOf(finalScore)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();

            // 6. 점수 가중치 정보 생성
            Map<String, Object> scoreWeights = Map.of(
                    "judgeWeight", JUDGE_WEIGHT * 100,
                    "aiWeight", AI_WEIGHT * 100,
                    "timeEfficiencyWeight", TIME_EFFICIENCY_WEIGHT * 100,
                    "judgeScore", round(judgeScore, 2),
                    "aiScore", round(aiScore, 2),
                    "timeEfficiencyScore", round(timeEfficiencyScore, 2));

            // 7. 결과 생성
            ScoreCalculationResult result = ScoreCalculationResult.builder()
                    .finalScore(finalScore)
                    .judgeScore(round(judgeScore, 2))
                    .aiScore(round(aiScore, 2))
                    .timeEfficiencyScore(round(timeEfficiencyScore, 2))
                    .scoreWeights(scoreWeights)
                    .scoreGrade(determineScoreGrade(finalScore))
                    .build();

            log.info("점수 계산 완료 - 최종점수: {}, 등급: {}", finalScore, result.getScoreGrade());
            return result;

        } catch (Exception e) {
            log.error("점수 계산 중 오류 발생", e);

            // 에러 시 기본 점수 반환
            return ScoreCalculationResult.builder()
                    .finalScore(0.0)
                    .judgeScore(0.0)
                    .aiScore(0.0)
                    .timeEfficiencyScore(0.0)
                    .scoreWeights(Map.of())
                    .scoreGrade("F")
                    .build();
        }
    }

    /**
     * Judge0 채점 결과 점수 계산
     */
    private double calculateJudgeScore(String judgeResult, int passedTests, int totalTests) {
        if (totalTests == 0)
            return 0.0;

        switch (judgeResult.toUpperCase()) {
            case "AC": // Accepted - 모든 테스트 통과
                return 100.0;

            case "WA": // Wrong Answer - 부분 점수
                double partialScore = (double) passedTests / totalTests * 100;
                return Math.max(partialScore * 0.5, 0); // 부분 점수는 최대 50%

            case "TLE": // Time Limit Exceeded - 시간 초과
                double timePartialScore = (double) passedTests / totalTests * 100;
                return Math.max(timePartialScore * 0.3, 0); // 시간초과는 최대 30%

            case "MLE": // Memory Limit Exceeded - 메모리 초과
                double memoryPartialScore = (double) passedTests / totalTests * 100;
                return Math.max(memoryPartialScore * 0.25, 0); // 메모리초과는 최대 25%

            case "RE": // Runtime Error - 실행 에러
                return Math.max((double) passedTests / totalTests * 10, 0); // 최대 10%

            case "CE": // Compilation Error - 컴파일 에러
                return 0.0;

            default: // PENDING 등 기타
                return 0.0;
        }
    }

    /**
     * 시간 효율성 점수 계산
     */
    private double calculateTimeEfficiencyScore(Integer solvingTimeSeconds, int timeLimitSeconds) {
        if (timeLimitSeconds <= 0 || solvingTimeSeconds == null || solvingTimeSeconds <= 0) {
            return 100.0; // 기본값
        }

        double timeRatio = (double) solvingTimeSeconds / timeLimitSeconds;

        if (timeRatio <= 0.3) { // 30% 이내 완료 - 매우 빠름
            return 100.0;
        } else if (timeRatio <= 0.5) { // 50% 이내 완료 - 빠름
            return 90.0 - (timeRatio - 0.3) * 200; // 90-50점 사이
        } else if (timeRatio <= 0.7) { // 70% 이내 완료 - 보통
            return 50.0 - (timeRatio - 0.5) * 100; // 50-30점 사이
        } else if (timeRatio <= 1.0) { // 제한 시간 내 완료 - 느림
            return 30.0 - (timeRatio - 0.7) * 100; // 30-0점 사이
        } else { // 제한 시간 초과
            return 0.0;
        }
    }

    /**
     * 최종 점수에 따른 등급 결정
     */
    private String determineScoreGrade(double score) {
        if (score >= 90)
            return "A+";
        if (score >= 80)
            return "A";
        if (score >= 70)
            return "B+";
        if (score >= 60)
            return "B";
        if (score >= 50)
            return "C+";
        if (score >= 40)
            return "C";
        if (score >= 30)
            return "D+";
        if (score >= 20)
            return "D";
        return "F";
    }

    /**
     * 소수점 반올림 헬퍼 메소드
     */
    private double round(double value, int places) {
        return BigDecimal.valueOf(value)
                .setScale(places, RoundingMode.HALF_UP)
                .doubleValue();
    }
}