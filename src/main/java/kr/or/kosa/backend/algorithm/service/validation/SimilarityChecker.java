package kr.or.kosa.backend.algorithm.service.validation;

import kr.or.kosa.backend.algorithm.dto.AlgoProblemDto;
import kr.or.kosa.backend.algorithm.dto.SimilarityThresholds;
import kr.or.kosa.backend.algorithm.dto.ValidationResultDto;
import kr.or.kosa.backend.algorithm.mapper.AlgorithmProblemMapper;
import kr.or.kosa.backend.algorithm.service.ProblemVectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

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

    // ===== Phase 3: 다단계 유사도 임계값 =====
    @Value("${algorithm.similarity.collected-threshold:0.85}")
    private double collectedThreshold;

    @Value("${algorithm.similarity.generated-threshold:0.75}")
    private double generatedThreshold;

    @Value("${algorithm.similarity.same-theme-threshold:0.65}")
    private double sameThemeThreshold;

    /**
     * 유사도 검사 (테마 없이 - 기존 호환성 유지)
     */
    public ValidationResultDto checkSimilarity(AlgoProblemDto newProblem) {
        return checkSimilarity(newProblem, null);
    }

    /**
     * 유사도 검사 (Phase 3: 테마 기반 다단계 검사)
     *
     * Vector DB 기반 소스별 유사도 검사 수행:
     * - 수집 데이터 (BOJ 등): 높은 임계값 (0.85)
     * - 생성 데이터: 중간 임계값 (0.75)
     * - 동일 테마: 낮은 임계값 (0.65)
     *
     * @param newProblem 새로 생성된 문제
     * @param theme      스토리 테마 (nullable)
     * @return 검증 결과
     */
    public ValidationResultDto checkSimilarity(AlgoProblemDto newProblem, String theme) {
        log.info("유사도 검사 시작 - 문제: {}, 테마: {}",
                newProblem != null ? newProblem.getAlgoProblemTitle() : "null", theme);

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
            // Phase 3: 다단계 유사도 검사 시도
            if (useVectorSimilarity) {
                ValidationResultDto vectorResult = checkMultiLevelVectorSimilarity(
                        newTitle, newDescription, theme, result);
                if (vectorResult != null) {
                    return vectorResult;
                }
                log.info("다단계 Vector DB 유사도 검사 실패, Jaccard 유사도로 폴백");
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
     * Phase 3: 다단계 Vector DB 기반 유사도 검사
     * 소스별로 다른 임계값 적용
     */
    private ValidationResultDto checkMultiLevelVectorSimilarity(
            String title, String description, String theme, ValidationResultDto result) {
        try {
            SimilarityThresholds thresholds = SimilarityThresholds.of(
                    collectedThreshold, generatedThreshold, sameThemeThreshold);

            Map<String, ProblemVectorStoreService.SimilarityCheckResult> checkResults =
                    vectorStoreService.checkSimilarityBySource(title, description, theme, thresholds);

            result.addMetadata("checkMethod", "VectorDB_MultiLevel");

            // 수집 데이터 검사 결과
            ProblemVectorStoreService.SimilarityCheckResult collectedResult = checkResults.get("COLLECTED");
            if (collectedResult != null) {
                result.addMetadata("collected_maxSimilarity",
                        Math.round(collectedResult.getMaxSimilarity() * 100) / 100.0);
                result.addMetadata("collected_threshold", collectedThreshold);
                result.addMetadata("collected_passed", collectedResult.isPassed());
            }

            // 생성 데이터 검사 결과
            ProblemVectorStoreService.SimilarityCheckResult generatedResult = checkResults.get("GENERATED");
            if (generatedResult != null) {
                result.addMetadata("generated_maxSimilarity",
                        Math.round(generatedResult.getMaxSimilarity() * 100) / 100.0);
                result.addMetadata("generated_threshold", generatedThreshold);
                result.addMetadata("generated_passed", generatedResult.isPassed());
            }

            // 동일 테마 검사 결과 (있는 경우)
            ProblemVectorStoreService.SimilarityCheckResult sameThemeResult = checkResults.get("SAME_THEME");
            if (sameThemeResult != null) {
                result.addMetadata("sameTheme_maxSimilarity",
                        Math.round(sameThemeResult.getMaxSimilarity() * 100) / 100.0);
                result.addMetadata("sameTheme_threshold", sameThemeThreshold);
                result.addMetadata("sameTheme_passed", sameThemeResult.isPassed());
            }

            // 종합 판정: 모든 검사 통과 여부
            boolean allPassed = checkResults.values().stream()
                    .allMatch(ProblemVectorStoreService.SimilarityCheckResult::isPassed);

            if (!allPassed) {
                // 실패한 검사 찾아서 에러 메시지 생성
                for (Map.Entry<String, ProblemVectorStoreService.SimilarityCheckResult> entry : checkResults.entrySet()) {
                    ProblemVectorStoreService.SimilarityCheckResult checkResult = entry.getValue();
                    if (!checkResult.isPassed()) {
                        String sourceType = switch (entry.getKey()) {
                            case "COLLECTED" -> "수집 데이터(BOJ 등)";
                            case "GENERATED" -> "AI 생성 데이터";
                            case "SAME_THEME" -> "동일 테마 데이터";
                            default -> entry.getKey();
                        };
                        result.addError(String.format(
                                "%s와 유사도가 높습니다 (유사도: %.1f%%, 기준: %.1f%%). 가장 유사한 문제: %s",
                                sourceType,
                                checkResult.getMaxSimilarity() * 100,
                                checkResult.getThreshold() * 100,
                                checkResult.getMostSimilarTitle()));
                    }
                }
            } else {
                log.info("다단계 Vector DB 유사도 검사 통과");
            }

            log.info("유사도 검사 완료 (VectorDB_MultiLevel) - 결과: {}", result.getSummary());
            return result;

        } catch (Exception e) {
            log.warn("다단계 Vector DB 유사도 검사 실패: {}", e.getMessage());
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
