package kr.or.kosa.backend.algorithm.service.validation;

import kr.or.kosa.backend.algorithm.dto.AlgoProblemDto;
import kr.or.kosa.backend.algorithm.dto.ValidationResultDto;
import kr.or.kosa.backend.algorithm.mapper.AlgorithmProblemMapper;
import kr.or.kosa.backend.algorithm.service.ProblemVectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Phase 4-4: 유사도 검사 서비스
 * Vector DB 기반으로 생성된 문제가 기존 문제와 유사한지 검사
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SimilarityChecker {

    private static final String VALIDATOR_NAME = "SimilarityChecker";

    private final AlgorithmProblemMapper problemMapper;
    private final ProblemVectorStoreService vectorStoreService;

    @Value("${algorithm.validation.max-similarity:0.8}")
    private double maxSimilarity;

    @Value("${algorithm.validation.similarity-check-limit:100}")
    private int similarityCheckLimit;

    @Value("${algorithm.validation.use-vector-similarity:true}")
    private boolean useVectorSimilarity;

    /**
     * 유사도 검사
     * Vector DB 기반 의미적 유사도 검사 우선, 실패 시 Jaccard 유사도 사용
     *
     * @param newProblem 새로 생성된 문제
     * @return 검증 결과
     */
    public ValidationResultDto checkSimilarity(AlgoProblemDto newProblem) {
        log.info("유사도 검사 시작 - 문제: {}", newProblem != null ? newProblem.getAlgoProblemTitle() : "null");

        ValidationResultDto result = ValidationResultDto.builder()
                .passed(true)
                .validatorName(VALIDATOR_NAME)
                .build();

        if (newProblem == null) {
            result.addError("문제 정보가 없습니다");
            return result;
        }

        String newTitle = newProblem.getAlgoProblemTitle();
        String newDescription = newProblem.getAlgoProblemDescription();

        if (newTitle == null || newTitle.isBlank()) {
            result.addError("문제 제목이 없습니다");
            return result;
        }

        if (newDescription == null || newDescription.isBlank()) {
            result.addError("문제 설명이 없습니다");
            return result;
        }

        try {
            // Vector DB 기반 유사도 검사 시도
            if (useVectorSimilarity) {
                ValidationResultDto vectorResult = checkVectorSimilarity(newTitle, newDescription, result);
                if (vectorResult != null) {
                    return vectorResult;
                }
                log.info("Vector DB 유사도 검사 실패, Jaccard 유사도로 폴백");
            }

            // Jaccard 유사도 검사 (폴백)
            return checkJaccardSimilarity(newTitle, newDescription, result);

        } catch (Exception e) {
            log.error("유사도 검사 중 오류 발생", e);
            result.addWarning("유사도 검사 중 오류 발생: " + e.getMessage());
        }

        log.info("유사도 검사 완료 - 결과: {}", result.getSummary());
        return result;
    }

    /**
     * Vector DB 기반 유사도 검사
     */
    private ValidationResultDto checkVectorSimilarity(String title, String description, ValidationResultDto result) {
        try {
            ProblemVectorStoreService.SimilarityCheckResult vectorResult =
                    vectorStoreService.checkSimilarity(title, description, maxSimilarity);

            result.addMetadata("checkMethod", "VectorDB");
            result.addMetadata("maxFoundSimilarity", Math.round(vectorResult.getMaxSimilarity() * 100) / 100.0);
            result.addMetadata("maxAllowedSimilarity", maxSimilarity);

            if (vectorResult.getMostSimilarTitle() != null) {
                result.addMetadata("mostSimilarTitle", vectorResult.getMostSimilarTitle());
                result.addMetadata("mostSimilarId", vectorResult.getMostSimilarId());
            }

            if (!vectorResult.isPassed()) {
                result.addError(String.format(
                        "기존 문제와 유사도가 너무 높습니다 (유사도: %.1f%%, 기준: %.1f%%). " +
                        "가장 유사한 문제: %s",
                        vectorResult.getMaxSimilarity() * 100, maxSimilarity * 100,
                        vectorResult.getMostSimilarTitle()));
            } else {
                log.info("Vector DB 유사도 검사 통과 - 최대 유사도: {}%",
                        String.format("%.1f", vectorResult.getMaxSimilarity() * 100));
            }

            log.info("유사도 검사 완료 (VectorDB) - 결과: {}", result.getSummary());
            return result;

        } catch (Exception e) {
            log.warn("Vector DB 유사도 검사 실패: {}", e.getMessage());
            return null;  // 폴백 신호
        }
    }

    /**
     * Jaccard 기반 유사도 검사 (폴백)
     */
    private ValidationResultDto checkJaccardSimilarity(String newTitle, String newDescription, ValidationResultDto result) {
        List<AlgoProblemDto> existingProblems = problemMapper.selectProblemsWithFilter(
                0, similarityCheckLimit, null, null, null, null);

        if (existingProblems == null || existingProblems.isEmpty()) {
            log.info("비교할 기존 문제가 없습니다");
            result.addMetadata("checkMethod", "Jaccard");
            result.addMetadata("checkedProblems", 0);
            result.addMetadata("maxFoundSimilarity", 0.0);
            return result;
        }

        double maxFoundSimilarity = 0.0;
        Long mostSimilarProblemId = null;
        String mostSimilarTitle = null;

        for (AlgoProblemDto existing : existingProblems) {
            double titleSimilarity = calculateJaccardSimilarity(
                    newTitle, existing.getAlgoProblemTitle());
            double descSimilarity = calculateJaccardSimilarity(
                    newDescription, existing.getAlgoProblemDescription());

            // 가중 평균 (제목 40%, 설명 60%)
            double combinedSimilarity = titleSimilarity * 0.4 + descSimilarity * 0.6;

            if (combinedSimilarity > maxFoundSimilarity) {
                maxFoundSimilarity = combinedSimilarity;
                mostSimilarProblemId = existing.getAlgoProblemId();
                mostSimilarTitle = existing.getAlgoProblemTitle();
            }
        }

        result.addMetadata("checkMethod", "Jaccard");
        result.addMetadata("checkedProblems", existingProblems.size());
        result.addMetadata("maxFoundSimilarity", Math.round(maxFoundSimilarity * 100) / 100.0);
        result.addMetadata("maxAllowedSimilarity", maxSimilarity);

        if (mostSimilarProblemId != null) {
            result.addMetadata("mostSimilarProblemId", mostSimilarProblemId);
            result.addMetadata("mostSimilarTitle", mostSimilarTitle);
        }

        if (maxFoundSimilarity > maxSimilarity) {
            result.addError(String.format(
                    "기존 문제와 유사도가 너무 높습니다 (유사도: %.1f%%, 기준: %.1f%%). " +
                    "가장 유사한 문제: [%d] %s",
                    maxFoundSimilarity * 100, maxSimilarity * 100,
                    mostSimilarProblemId, mostSimilarTitle));
        } else {
            log.info("Jaccard 유사도 검사 통과 - 최대 유사도: {}%", String.format("%.1f", maxFoundSimilarity * 100));
        }

        log.info("유사도 검사 완료 (Jaccard) - 결과: {}", result.getSummary());
        return result;
    }

    /**
     * Jaccard 유사도 계산
     * 두 문자열을 토큰화하여 교집합/합집합 비율 계산
     */
    private double calculateJaccardSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) {
            return 0.0;
        }

        // 공백 기준 토큰화 및 정규화
        String[] tokens1 = normalizeAndTokenize(text1);
        String[] tokens2 = normalizeAndTokenize(text2);

        if (tokens1.length == 0 || tokens2.length == 0) {
            return 0.0;
        }

        // Set으로 변환
        java.util.Set<String> set1 = new java.util.HashSet<>(java.util.Arrays.asList(tokens1));
        java.util.Set<String> set2 = new java.util.HashSet<>(java.util.Arrays.asList(tokens2));

        // 교집합 계산
        java.util.Set<String> intersection = new java.util.HashSet<>(set1);
        intersection.retainAll(set2);

        // 합집합 계산
        java.util.Set<String> union = new java.util.HashSet<>(set1);
        union.addAll(set2);

        if (union.isEmpty()) {
            return 0.0;
        }

        return (double) intersection.size() / union.size();
    }

    /**
     * 텍스트 정규화 및 토큰화
     */
    private String[] normalizeAndTokenize(String text) {
        if (text == null) {
            return new String[0];
        }

        // 소문자 변환, 특수문자 제거, 공백 기준 분리
        return text.toLowerCase()
                .replaceAll("[^a-z0-9가-힣\\s]", " ")
                .trim()
                .split("\\s+");
    }
}
