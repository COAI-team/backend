package kr.or.kosa.backend.algorithm.service.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.algorithm.dto.AlgoProblemDto;
import kr.or.kosa.backend.algorithm.dto.AlgoTestcaseDto;
import kr.or.kosa.backend.algorithm.dto.ValidationResultDto;
import kr.or.kosa.backend.algorithm.dto.response.ProblemGenerationResponseDto;
import kr.or.kosa.backend.algorithm.service.LLMChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 4-5: Self-Correction 서비스
 * 검증 실패 시 LLM을 통해 문제를 수정하고 재검증
 *
 * Phase 6 개선:
 * - 검증기별 맞춤 가이드라인 제공
 * - 구체적인 수정 방향 안내로 Self-Correction 성공률 향상
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelfCorrectionService {

    private final LLMChatService llmChatService;
    private final ObjectMapper objectMapper;

    @Value("${algorithm.validation.max-correction-attempts:3}")
    private int maxCorrectionAttempts;

    /**
     * 검증 실패에 대한 자동 수정 시도
     *
     * @param problem        원본 문제
     * @param testCases      원본 테스트케이스
     * @param optimalCode    최적 풀이 코드
     * @param naiveCode      비효율 풀이 코드
     * @param validationResults 실패한 검증 결과들
     * @param attemptNumber  현재 시도 횟수
     * @return 수정된 문제 생성 응답
     */
    public ProblemGenerationResponseDto attemptCorrection(
            AlgoProblemDto problem,
            List<AlgoTestcaseDto> testCases,
            String optimalCode,
            String naiveCode,
            List<ValidationResultDto> validationResults,
            int attemptNumber) {

        log.info("Self-Correction 시도 #{} 시작 - 문제: {}",
                attemptNumber, problem.getAlgoProblemTitle());

        if (attemptNumber > maxCorrectionAttempts) {
            log.warn("최대 수정 시도 횟수 초과 ({}/{})", attemptNumber, maxCorrectionAttempts);
            return null;
        }

        // Phase 6: 검증기별 맞춤 가이드라인 수집 (기존 오류 요약 대신 사용)
        String validatorGuidelines = collectValidatorSpecificGuidelines(validationResults);

        // Self-Correction 프롬프트 생성 (검증기별 가이드라인 적용)
        String correctionPrompt = buildCorrectionPrompt(problem, testCases, optimalCode, naiveCode, validatorGuidelines);

        try {
            // LLM에 수정 요청
            String correctedResponse = llmChatService.generate(correctionPrompt);

            // 응답 파싱 (원본 데이터 유지하면서 파싱)
            ProblemGenerationResponseDto correctedProblem = parseCorrection(
                    correctedResponse, problem, testCases, optimalCode, naiveCode);

            if (correctedProblem != null) {
                log.info("Self-Correction #{} 완료 - 수정된 문제 생성됨", attemptNumber);
            }

            return correctedProblem;

        } catch (Exception e) {
            log.error("Self-Correction #{} 실패", attemptNumber, e);
            return null;
        }
    }

    /**
     * Phase 6: 검증기별 맞춤 가이드라인 수집
     * 각 검증기의 실패 원인에 따라 구체적인 수정 방향 제공
     */
    private String collectValidatorSpecificGuidelines(List<ValidationResultDto> validationResults) {
        StringBuilder guidelines = new StringBuilder();

        for (ValidationResultDto result : validationResults) {
            if (!result.isPassed()) {
                guidelines.append(buildValidatorSpecificGuideline(result));
                guidelines.append("\n");
            }
        }

        return guidelines.toString();
    }

    /**
     * Phase 6: 검증기별 맞춤 가이드라인 생성
     *
     * 구현 논리:
     * - 각 검증기의 특성에 맞는 구체적인 수정 방향 제공
     * - 일반적인 오류 메시지 대신 실행 가능한 해결책 안내
     * - 이를 통해 Self-Correction 성공률 향상
     */
    private String buildValidatorSpecificGuideline(ValidationResultDto result) {
        String validatorName = result.getValidatorName();
        String errors = String.join("; ", result.getErrors());

        return switch (validatorName) {
            case "StructureValidator" -> String.format("""
                #### 구조 검증 실패
                **오류:** %s
                **해결 방법:**
                1. 필수 필드(제목, 설명, 입출력 형식, 제약조건)를 모두 포함하세요
                2. 테스트 케이스를 최소 3개 이상 생성하세요
                3. 설명이 50자 이상이 되도록 충분히 작성하세요
                4. optimalCode와 naiveCode를 반드시 포함하세요
                """, errors);

            case "SimilarityChecker" -> String.format("""
                #### 유사도 검사 실패
                **오류:** %s
                **해결 방법:**
                1. 문제 스토리를 **완전히 새로운 상황**으로 변경하세요
                2. 변수명/상황설정이 **근본적으로 다르게** 하세요
                3. 같은 알고리즘이라도 다른 소재를 사용하세요
                4. 예: "배낭문제" → "선물 상자 최적화", "최단경로" → "선물 배달 경로"
                """, errors);

            case "CodeExecutionValidator" -> String.format("""
                #### 코드 실행 검증 실패
                **오류:** %s
                **해결 방법:**
                1. optimalCode의 문법 오류를 수정하세요
                2. 모든 테스트 케이스가 통과하도록 확인하세요
                3. 입력 형식이 문제 설명과 일치하는지 확인하세요
                4. 출력 형식이 예제와 정확히 일치하는지 확인하세요
                5. 코드 마지막에 함수 호출이 있는지 확인하세요 (예: solve() 호출)
                """, errors);

            case "TimeRatioValidator" -> String.format("""
                #### 시간 비율 검증 실패
                **오류:** %s
                **해결 방법:**
                1. optimalCode가 효율적인 알고리즘을 사용하는지 확인하세요
                2. naiveCode는 반드시 비효율적인 완전탐색을 사용하세요
                3. naive와 optimal의 시간복잡도 차이가 명확해야 합니다
                4. 예: optimal O(N log N) vs naive O(N²) 또는 O(2^N)
                5. naiveCode가 정확한 결과를 출력하는지 확인하세요 (출력 일치 필수)
                """, errors);

            default -> String.format("""
                #### %s 실패
                **오류:** %s
                **해결 방법:** 위 오류 내용을 참고하여 수정하세요.
                """, validatorName, errors);
        };
    }

    /**
     * Self-Correction 프롬프트 생성
     */
    private String buildCorrectionPrompt(
            AlgoProblemDto problem,
            List<AlgoTestcaseDto> testCases,
            String optimalCode,
            String naiveCode,
            String errorSummary) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("## 알고리즘 문제 수정 요청\n\n");

        prompt.append("### 원본 문제\n");
        prompt.append("- 제목: ").append(problem.getAlgoProblemTitle()).append("\n");
        prompt.append("- 설명: ").append(problem.getAlgoProblemDescription()).append("\n");
        prompt.append("- 난이도: ").append(problem.getAlgoProblemDifficulty()).append("\n");
        prompt.append("- 시간 제한: ").append(problem.getTimelimit()).append("ms\n");
        prompt.append("- 메모리 제한: ").append(problem.getMemorylimit()).append("MB\n\n");

        int testCaseCount = (testCases != null) ? testCases.size() : 0;
        prompt.append("### 테스트케이스 (").append(testCaseCount).append("개)\n");
        if (testCases != null && !testCases.isEmpty()) {
            for (int i = 0; i < Math.min(testCases.size(), 3); i++) {
                AlgoTestcaseDto tc = testCases.get(i);
                prompt.append("입력: ").append(truncate(tc.getInputData(), 100)).append("\n");
                prompt.append("출력: ").append(truncate(tc.getExpectedOutput(), 100)).append("\n\n");
            }
        } else {
            prompt.append("(테스트케이스 없음 - 새로 생성 필요)\n\n");
        }

        if (optimalCode != null && !optimalCode.isBlank()) {
            prompt.append("### 최적 풀이 코드\n```\n");
            prompt.append(truncate(optimalCode, 500));
            prompt.append("\n```\n\n");
        }

        if (naiveCode != null && !naiveCode.isBlank()) {
            prompt.append("### 비효율 풀이 코드\n```\n");
            prompt.append(truncate(naiveCode, 500));
            prompt.append("\n```\n\n");
        }

        prompt.append("### 검증 실패 내역\n");
        prompt.append(errorSummary).append("\n");

        prompt.append("### 수정 요청\n");
        prompt.append("위 검증 실패 내역을 해결하도록 문제, 테스트케이스, 또는 풀이 코드를 수정해주세요.\n");
        prompt.append("응답은 다음 JSON 형식으로 제공해주세요:\n\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"problem\": { \"title\": \"...\", \"description\": \"...\", ... },\n");
        prompt.append("  \"testCases\": [ { \"inputData\": \"...\", \"expectedOutput\": \"...\" }, ... ],\n");
        prompt.append("  \"optimalCode\": \"...\",\n");
        prompt.append("  \"naiveCode\": \"...\",\n");
        prompt.append("  \"correctionNotes\": \"수정 내용 설명\"\n");
        prompt.append("}\n");
        prompt.append("```\n");

        return prompt.toString();
    }

    /**
     * LLM 응답 파싱
     * 파싱 실패 시 원본 데이터 유지
     */
    private ProblemGenerationResponseDto parseCorrection(
            String response,
            AlgoProblemDto originalProblem,
            List<AlgoTestcaseDto> originalTestCases,
            String originalOptimalCode,
            String originalNaiveCode) {
        try {
            log.debug("Self-Correction 응답 파싱 중...");

            // JSON 정제 (마크다운 코드 블록 제거 등)
            String cleanedJson = sanitizeJson(response);

            if (cleanedJson == null || cleanedJson.isBlank() || cleanedJson.equals("{}")) {
                log.warn("Self-Correction 응답이 비어있음, 원본 데이터 반환");
                return buildResponseWithOriginals(originalProblem, originalTestCases, originalOptimalCode, originalNaiveCode);
            }

            JsonNode root = objectMapper.readTree(cleanedJson);

            // 수정된 문제 정보 파싱 (없으면 원본 유지)
            AlgoProblemDto correctedProblem = parseCorrectedProblem(root, originalProblem);

            // 수정된 테스트케이스 파싱 (없으면 원본 유지)
            List<AlgoTestcaseDto> correctedTestCases = parseCorrectedTestCases(root, originalTestCases);

            // 수정된 코드 파싱 (없으면 원본 유지)
            String correctedOptimalCode = getTextOrDefault(root, "optimalCode", originalOptimalCode);
            String correctedNaiveCode = getTextOrDefault(root, "naiveCode", originalNaiveCode);

            // 수정 내용 로깅
            String correctionNotes = getText(root, "correctionNotes");
            if (correctionNotes != null && !correctionNotes.isBlank()) {
                log.info("Self-Correction 수정 내용: {}", correctionNotes);
            }

            // 변경 사항 요약 로그
            logChangeSummary(originalProblem, correctedProblem,
                            originalTestCases, correctedTestCases,
                            originalOptimalCode, correctedOptimalCode,
                            originalNaiveCode, correctedNaiveCode);

            return ProblemGenerationResponseDto.builder()
                    .problem(correctedProblem)
                    .testCases(correctedTestCases)
                    .optimalCode(correctedOptimalCode)
                    .naiveCode(correctedNaiveCode)
                    .build();

        } catch (Exception e) {
            log.error("Self-Correction 응답 파싱 실패: {}", e.getMessage());
            log.debug("파싱 실패한 응답: {}", truncate(response, 500));
            // 파싱 실패 시 원본 데이터 반환
            return buildResponseWithOriginals(originalProblem, originalTestCases, originalOptimalCode, originalNaiveCode);
        }
    }

    /**
     * JSON 응답 정제 - 마크다운 코드 블록 제거
     */
    private String sanitizeJson(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return "{}";
        }

        return rawResponse
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*$", "")
                .replaceAll("```", "")
                .trim();
    }

    /**
     * 수정된 문제 정보 파싱
     */
    private AlgoProblemDto parseCorrectedProblem(JsonNode root, AlgoProblemDto original) {
        JsonNode problemNode = root.get("problem");

        if (problemNode == null || problemNode.isNull()) {
            log.debug("problem 필드 없음, 원본 문제 유지");
            return original;
        }

        // 원본을 기반으로 수정된 필드만 업데이트
        return AlgoProblemDto.builder()
                .algoProblemTitle(getTextOrDefault(problemNode, "title", original.getAlgoProblemTitle()))
                .algoProblemDescription(getTextOrDefault(problemNode, "description", original.getAlgoProblemDescription()))
                .algoProblemDifficulty(original.getAlgoProblemDifficulty()) // 난이도는 변경하지 않음
                .algoProblemSource(original.getAlgoProblemSource())
                .problemType(original.getProblemType())
                .constraints(getTextOrDefault(problemNode, "constraints", original.getConstraints()))
                .inputFormat(getTextOrDefault(problemNode, "inputFormat", original.getInputFormat()))
                .outputFormat(getTextOrDefault(problemNode, "outputFormat", original.getOutputFormat()))
                .expectedTimeComplexity(getTextOrDefault(problemNode, "expectedTimeComplexity", original.getExpectedTimeComplexity()))
                .timelimit(original.getTimelimit())
                .memorylimit(original.getMemorylimit())
                .algoProblemTags(original.getAlgoProblemTags())
                .algoProblemStatus(original.getAlgoProblemStatus())
                .algoCreatedAt(original.getAlgoCreatedAt())
                .algoUpdatedAt(original.getAlgoUpdatedAt())
                .build();
    }

    /**
     * 수정된 테스트케이스 파싱
     */
    private List<AlgoTestcaseDto> parseCorrectedTestCases(JsonNode root, List<AlgoTestcaseDto> original) {
        JsonNode testCasesNode = root.get("testCases");

        if (testCasesNode == null || !testCasesNode.isArray() || testCasesNode.isEmpty()) {
            log.debug("testCases 필드 없거나 비어있음, 원본 테스트케이스 유지");
            return original != null ? original : List.of();
        }

        List<AlgoTestcaseDto> correctedTestCases = new ArrayList<>();
        int sampleIndex = 0;

        for (JsonNode tcNode : testCasesNode) {
            String inputData = getText(tcNode, "inputData");
            if (inputData == null) {
                inputData = getText(tcNode, "input"); // 대체 필드명
            }

            String expectedOutput = getText(tcNode, "expectedOutput");
            if (expectedOutput == null) {
                expectedOutput = getText(tcNode, "output"); // 대체 필드명
            }

            if (inputData == null || inputData.isBlank()) {
                log.debug("입력 데이터 없는 테스트케이스 건너뜀");
                continue;
            }

            boolean isSample = sampleIndex < 2; // 처음 2개는 샘플로 설정
            sampleIndex++;

            correctedTestCases.add(AlgoTestcaseDto.builder()
                    .inputData(inputData)
                    .expectedOutput(expectedOutput) // null일 수 있음 (Code-First에서 재생성 필요)
                    .isSample(isSample)
                    .build());
        }

        if (correctedTestCases.isEmpty()) {
            log.debug("파싱된 테스트케이스 없음, 원본 유지");
            return original != null ? original : List.of();
        }

        log.info("테스트케이스 수정됨: 원본 {}개 → 수정 {}개",
                original != null ? original.size() : 0, correctedTestCases.size());

        return correctedTestCases;
    }

    /**
     * 원본 데이터로 응답 생성
     */
    private ProblemGenerationResponseDto buildResponseWithOriginals(
            AlgoProblemDto problem,
            List<AlgoTestcaseDto> testCases,
            String optimalCode,
            String naiveCode) {
        return ProblemGenerationResponseDto.builder()
                .problem(problem)
                .testCases(testCases != null ? testCases : List.of())
                .optimalCode(optimalCode)
                .naiveCode(naiveCode)
                .build();
    }

    /**
     * JSON 필드에서 텍스트 추출
     */
    private String getText(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode fieldNode = node.get(field);
        if (fieldNode == null || fieldNode.isNull()) {
            return null;
        }
        return fieldNode.asText();
    }

    /**
     * JSON 필드에서 텍스트 추출 (기본값 지원)
     */
    private String getTextOrDefault(JsonNode node, String field, String defaultValue) {
        String value = getText(node, field);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    /**
     * 변경 사항 요약 로그
     */
    private void logChangeSummary(
            AlgoProblemDto originalProblem, AlgoProblemDto correctedProblem,
            List<AlgoTestcaseDto> originalTestCases, List<AlgoTestcaseDto> correctedTestCases,
            String originalOptimalCode, String correctedOptimalCode,
            String originalNaiveCode, String correctedNaiveCode) {

        StringBuilder changes = new StringBuilder("Self-Correction 변경 사항: ");
        boolean hasChanges = false;

        // 제목 변경 확인
        if (!safeEquals(originalProblem.getAlgoProblemTitle(), correctedProblem.getAlgoProblemTitle())) {
            changes.append("[제목 수정] ");
            hasChanges = true;
        }

        // 설명 변경 확인
        if (!safeEquals(originalProblem.getAlgoProblemDescription(), correctedProblem.getAlgoProblemDescription())) {
            changes.append("[설명 수정] ");
            hasChanges = true;
        }

        // 테스트케이스 변경 확인
        int originalCount = originalTestCases != null ? originalTestCases.size() : 0;
        int correctedCount = correctedTestCases != null ? correctedTestCases.size() : 0;
        if (originalCount != correctedCount) {
            changes.append(String.format("[테스트케이스 %d→%d개] ", originalCount, correctedCount));
            hasChanges = true;
        }

        // optimalCode 변경 확인
        if (!safeEquals(originalOptimalCode, correctedOptimalCode)) {
            changes.append("[optimalCode 수정] ");
            hasChanges = true;
        }

        // naiveCode 변경 확인
        if (!safeEquals(originalNaiveCode, correctedNaiveCode)) {
            changes.append("[naiveCode 수정] ");
            hasChanges = true;
        }

        if (hasChanges) {
            log.info(changes.toString());
        } else {
            log.debug("Self-Correction: 변경 사항 없음 (원본 유지)");
        }
    }

    /**
     * null-safe 문자열 비교
     */
    private boolean safeEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    /**
     * 문자열 길이 제한
     */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    /**
     * 최대 시도 횟수 확인
     */
    public boolean canAttemptMore(int currentAttempt) {
        return currentAttempt < maxCorrectionAttempts;
    }

    /**
     * 최대 시도 횟수 반환
     */
    public int getMaxAttempts() {
        return maxCorrectionAttempts;
    }
}
