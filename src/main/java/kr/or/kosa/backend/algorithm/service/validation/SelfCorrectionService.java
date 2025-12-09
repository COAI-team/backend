package kr.or.kosa.backend.algorithm.service.validation;

import kr.or.kosa.backend.algorithm.dto.AlgoProblemDto;
import kr.or.kosa.backend.algorithm.dto.AlgoTestcaseDto;
import kr.or.kosa.backend.algorithm.dto.ValidationResultDto;
import kr.or.kosa.backend.algorithm.dto.response.ProblemGenerationResponseDto;
import kr.or.kosa.backend.algorithm.service.LLMChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Phase 4-5: Self-Correction 서비스
 * 검증 실패 시 LLM을 통해 문제를 수정하고 재검증
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelfCorrectionService {

    private final LLMChatService llmChatService;

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

        // 실패한 검증 결과들의 오류 메시지 수집
        String errorSummary = collectErrorSummary(validationResults);

        // Self-Correction 프롬프트 생성
        String correctionPrompt = buildCorrectionPrompt(problem, testCases, optimalCode, naiveCode, errorSummary);

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
     * 검증 오류 요약 수집
     */
    private String collectErrorSummary(List<ValidationResultDto> validationResults) {
        StringBuilder summary = new StringBuilder();

        for (ValidationResultDto result : validationResults) {
            if (!result.isPassed()) {
                summary.append("- [").append(result.getValidatorName()).append("] ");
                summary.append(String.join("; ", result.getErrors()));
                summary.append("\n");
            }

            if (!result.getWarnings().isEmpty()) {
                summary.append("- [").append(result.getValidatorName()).append(" 경고] ");
                summary.append(String.join("; ", result.getWarnings()));
                summary.append("\n");
            }
        }

        return summary.toString();
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

            // TODO: 실제 구현에서는 LLMResponseParser를 사용하여 응답 파싱
            // 현재는 원본 데이터를 유지하면서 반환 (LLM 응답에서 수정된 부분만 업데이트)

            // 원본 데이터를 유지하면서 반환
            // 실제 구현 시 LLM 응답에서 수정된 데이터를 파싱하여 적용
            return ProblemGenerationResponseDto.builder()
                    .problem(originalProblem)
                    .testCases(originalTestCases != null ? originalTestCases : List.of())
                    .optimalCode(originalOptimalCode)
                    .naiveCode(originalNaiveCode)
                    .build();

        } catch (Exception e) {
            log.error("Self-Correction 응답 파싱 실패", e);
            return null;
        }
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
