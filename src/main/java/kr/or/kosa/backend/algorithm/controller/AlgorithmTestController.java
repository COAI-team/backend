package kr.or.kosa.backend.algorithm.controller;

import kr.or.kosa.backend.algorithm.dto.ProblemSolveResponseDto;
import kr.or.kosa.backend.algorithm.dto.SubmissionRequestDto;
import kr.or.kosa.backend.algorithm.dto.SubmissionResponseDto;
import kr.or.kosa.backend.algorithm.domain.ProgrammingLanguage;
import kr.or.kosa.backend.algorithm.service.AlgorithmSolvingService;
import kr.or.kosa.backend.algorithm.service.CodeEvaluationService;
import kr.or.kosa.backend.algorithm.dto.ScoreCalculationParams;
import kr.or.kosa.backend.algorithm.service.ScoreCalculator;
import kr.or.kosa.backend.commons.response.ApiResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 알고리즘 기능 테스트용 컨트롤러 (JWT 없이 테스트용)
 * 기존 기능 + AI 코드 평가 + 점수 계산 테스트 추가
 */
@RestController
@RequestMapping("/api/algo/test")
@RequiredArgsConstructor
@Slf4j
public class AlgorithmTestController {

    private final AlgorithmSolvingService solvingService;
    private final CodeEvaluationService codeEvaluationService;  // ✅ 추가
    private final ScoreCalculator scoreCalculator;              // ✅ 추가

    // ==================== 기존 기능들 ====================

    /**
     * 문제 풀이 시작 테스트 (JWT 없이)
     */
    @GetMapping("/problems/{problemId}/solve")
    public ApiResponse<ProblemSolveResponseDto> testStartProblemSolving(
            @PathVariable("problemId") Long problemId,
            @RequestParam(defaultValue = "1") Long userId) {

        log.info("문제 풀이 시작 테스트 - problemId: {}, userId: {}", problemId, userId);

        try {
            ProblemSolveResponseDto response = solvingService.startProblemSolving(problemId, userId);
            return ApiResponse.success(response);

        } catch (Exception e) {
            log.error("문제 풀이 시작 테스트 실패", e);
            return ApiResponse.error("T400", "테스트 실패: " + e.getMessage());
        }
    }

    /**
     * 간단한 코드 제출 테스트 (임시: Python 고정)
     */
    @PostMapping("/submissions/simple")
    public ApiResponse<SubmissionResponseDto> testSimpleSubmission(
            @RequestParam Long problemId,
            @RequestParam(defaultValue = "1") Long userId) {

        // 🐍 Python 기본 코드 (두 수의 합 테스트용)
        String sampleCode = """
                a, b = map(int, input().split())
                print(a + b)
                """;

        SubmissionRequestDto request = SubmissionRequestDto.builder()
                .problemId(problemId)
                .language(ProgrammingLanguage.PYTHON)   // 🔥 강제로 PYTHON 사용
                .sourceCode(sampleCode)
                .startTime(LocalDateTime.now().minusMinutes(5))
                .endTime(LocalDateTime.now())
                .build();

        try {
            SubmissionResponseDto response = solvingService.submitCode(request, userId);
            return ApiResponse.success(response);

        } catch (Exception e) {
            log.error("코드 제출 테스트 실패", e);
            return ApiResponse.error("T401", "테스트 실패: " + e.getMessage());
        }
    }

    /**
     * 제출 결과 조회 테스트
     */
    @GetMapping("/submissions/{submissionId}")
    public ApiResponse<SubmissionResponseDto> testGetSubmissionResult(
            @PathVariable("submissionId") Long submissionId,
            @RequestParam(defaultValue = "1") Long userId) {

        try {
            SubmissionResponseDto response = solvingService.getSubmissionResult(submissionId, userId);
            return ApiResponse.success(response);

        } catch (Exception e) {
            log.error("제출 결과 조회 테스트 실패", e);
            return ApiResponse.error("T402", "테스트 실패: " + e.getMessage());
        }
    }

    /**
     * 사용자 제출 이력 테스트
     */
    @GetMapping("/submissions/user/{userId}")
    public ApiResponse<?> testGetUserSubmissions(
            @PathVariable("userId") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            var response = solvingService.getUserSubmissions(userId, page, size);
            return ApiResponse.success(response);

        } catch (Exception e) {
            log.error("사용자 제출 이력 조회 테스트 실패", e);
            return ApiResponse.error("T403", "테스트 실패: " + e.getMessage());
        }
    }

    /**
     * 공유 상태 업데이트 테스트
     */
    @PatchMapping("/submissions/{submissionId}/visibility")
    public ApiResponse<Void> testUpdateSharingStatus(
            @PathVariable("submissionId") Long submissionId,
            @RequestParam Boolean isShared,
            @RequestParam(defaultValue = "1") Long userId) {

        try {
            solvingService.updateSharingStatus(submissionId, isShared, userId);
            return ApiResponse.success(null);

        } catch (Exception e) {
            log.error("공유 상태 업데이트 테스트 실패", e);
            return ApiResponse.error("T404", "테스트 실패: " + e.getMessage());
        }
    }

    // ==================== ✅ 새로 추가된 AI 기능들 (수정됨) ====================

    /**
     * AI 코드 평가 테스트
     * POST /api/algo/test/evaluate
     */
    @PostMapping("/evaluate")
    public ApiResponse<?> testCodeEvaluation(@RequestBody CodeEvaluationTestRequest request) {
        try {
            log.info("AI 코드 평가 테스트 시작 - 언어: {}", request.getLanguage());

            // ✅ 타입 수정: AICodeEvaluationResult로 명시적 타입 지정
            CompletableFuture<?> future = codeEvaluationService.evaluateCode(
                    request.getSourceCode(),
                    request.getProblemDescription(),
                    request.getLanguage(),
                    request.getJudgeResult()
            );

            // 비동기 결과 대기 (테스트용)
            Object result = future.get();

            // ✅ ApiResponse 수정: success(data)만 사용
            return ApiResponse.success(Map.of(
                    "message", "AI 코드 평가 완료",
                    "result", result
            ));

        } catch (Exception e) {
            log.error("AI 코드 평가 테스트 실패", e);
            return ApiResponse.error("T500", "AI 코드 평가 테스트 실패: " + e.getMessage());
        }
    }

    /**
     * 점수 계산 테스트
     * POST /api/algo/test/score
     */
    @PostMapping("/score")
    public ApiResponse<?> testScoreCalculation(@RequestBody ScoreTestRequest request) {
        try {
            log.info("점수 계산 테스트 시작 - Judge: {}", request.getJudgeResult());

            // ✅ ScoreCalculationParams 생성 (public class로 변경 필요)
            var params = ScoreCalculationParams.builder()
                    .judgeResult(request.getJudgeResult())
                    .passedTestCount(request.getPassedTestCount())
                    .totalTestCount(request.getTotalTestCount())
                    .aiScore(request.getAiScore())
                    .solvingTimeSeconds(request.getSolvingTimeSeconds())
                    .timeLimitSeconds(request.getTimeLimitSeconds())
                    .build();

            var result = scoreCalculator.calculateFinalScore(params);

            // ✅ ApiResponse 수정
            return ApiResponse.success(Map.of(
                    "message", "점수 계산 완료",
                    "result", result
            ));

        } catch (Exception e) {
            log.error("점수 계산 테스트 실패", e);
            return ApiResponse.error("T501", "점수 계산 테스트 실패: " + e.getMessage());
        }
    }

    /**
     * 통합 테스트 (AI 평가 + 점수 계산)
     * POST /api/algo/test/full
     */
    @PostMapping("/full")
    public ApiResponse<?> testFullFlow(@RequestBody FullTestRequest request) {
        try {
            log.info("통합 테스트 시작");

            // 1. AI 코드 평가
            CompletableFuture<?> evaluationFuture = codeEvaluationService.evaluateCode(
                    request.getSourceCode(),
                    request.getProblemDescription(),
                    request.getLanguage(),
                    request.getJudgeResult()
            );

            Object aiResult = evaluationFuture.get();

            // 2. 점수 계산 (AI 점수 포함)
            var scoreParams = ScoreCalculationParams.builder()
                    .judgeResult(request.getJudgeResult())
                    .passedTestCount(request.getPassedTestCount())
                    .totalTestCount(request.getTotalTestCount())
                    .aiScore(request.getAiScore() != null ? request.getAiScore() : 75.0) // 기본값
                    .solvingTimeSeconds(request.getSolvingTimeSeconds())
                    .timeLimitSeconds(1800) // 30분 기본값
                    .build();

            var scoreResult = scoreCalculator.calculateFinalScore(scoreParams);

            // 3. 통합 결과 반환
            return ApiResponse.success(Map.of(
                    "message", "통합 테스트 완료",
                    "aiEvaluation", aiResult,
                    "scoreCalculation", scoreResult
            ));

        } catch (Exception e) {
            log.error("통합 테스트 실패", e);
            return ApiResponse.error("T502", "통합 테스트 실패: " + e.getMessage());
        }
    }

    /**
     * 🔥 빠른 코드 평가 테스트 (샘플 데이터)
     * GET /api/algo/test/quick-evaluate
     */
    @GetMapping("/quick-evaluate")
    public ApiResponse<?> testQuickEvaluation() {
        try {
            String sampleCode = """
                    public class Solution {
                        public int twoSum(int a, int b) {
                            return a + b;
                        }
                    }
                    """;

            CompletableFuture<?> future = codeEvaluationService.evaluateCode(
                    sampleCode,
                    "두 정수를 입력받아 합을 출력하는 프로그램을 작성하시오.",
                    "JAVA",
                    "AC"
            );

            Object result = future.get();
            return ApiResponse.success(Map.of(
                    "message", "빠른 평가 완료",
                    "result", result
            ));

        } catch (Exception e) {
            log.error("빠른 평가 테스트 실패", e);
            return ApiResponse.error("T503", "빠른 평가 테스트 실패: " + e.getMessage());
        }
    }
}

// ===== ✅ 테스트용 DTO 클래스들 (내부 클래스로 정의) =====

/**
 * AI 코드 평가 테스트 요청 DTO
 */
@lombok.Data
class CodeEvaluationTestRequest {
    private String sourceCode;
    private String problemDescription;
    private String language;
    private String judgeResult;
}

/**
 * 점수 계산 테스트 요청 DTO
 */
@Data
class ScoreTestRequest {
    private String judgeResult;
    private Integer passedTestCount;
    private Integer totalTestCount;
    private Double aiScore;
    private Integer solvingTimeSeconds;
    private Integer timeLimitSeconds;
}

/**
 * 통합 테스트 요청 DTO
 */
@Data
class FullTestRequest {
    private String sourceCode;
    private String problemDescription;
    private String language;
    private String judgeResult;
    private Integer passedTestCount;
    private Integer totalTestCount;
    private Double aiScore;
    private Integer solvingTimeSeconds;
}