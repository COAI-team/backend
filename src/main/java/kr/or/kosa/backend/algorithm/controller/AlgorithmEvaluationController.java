package kr.or.kosa.backend.algorithm.controller;

import kr.or.kosa.backend.algorithm.service.AlgorithmEvaluationService;
import kr.or.kosa.backend.commons.response.ApiResponse;
import kr.or.kosa.backend.security.jwt.JwtAuthentication;
import kr.or.kosa.backend.security.jwt.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

/**
 * 알고리즘 평가 전담 컨트롤러
 * - AI 평가 상태 조회
 * - AI 평가 재실행
 * - 평가 프로세스 모니터링
 */
@RestController
@RequestMapping("/algo/evaluation")
@RequiredArgsConstructor
@Slf4j
public class AlgorithmEvaluationController {

    private final AlgorithmEvaluationService evaluationService;

    /**
     * JWT에서 userId 추출 (기존 컨트롤러와 동일한 방식)
     */
    private Long extractUserId(JwtAuthentication authentication) {
        if (authentication == null) {
            throw new IllegalStateException("인증 정보가 없습니다.");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof JwtUserDetails userDetails)) {
            throw new IllegalStateException("JWT 사용자 정보가 올바르지 않습니다.");
        }

        return userDetails.id().longValue();
    }

    /**
     * 평가 상태 조회 (JWT 필요)
     * GET /api/algo/evaluation/status/{submissionId}
     */
    @GetMapping("/status/{submissionId}")
    public ResponseEntity<ApiResponse<AlgorithmEvaluationService.EvaluationStatusDto>> getEvaluationStatus(
            @PathVariable("submissionId") Long submissionId,
            @AuthenticationPrincipal JwtAuthentication authentication) {

        Long userId = extractUserId(authentication);

        log.info("평가 상태 조회 - submissionId: {}, userId: {}", submissionId, userId);

        try {
            AlgorithmEvaluationService.EvaluationStatusDto status =
                    evaluationService.getEvaluationStatus(submissionId);

            return ResponseEntity.ok(
                    new ApiResponse<>("0000", "평가 상태 조회 완료", status)
            );

        } catch (IllegalArgumentException e) {
            log.warn("평가 상태 조회 실패 - submissionId: {}, error: {}", submissionId, e.getMessage());
            return ResponseEntity.badRequest().body(
                    new ApiResponse<>("4000", e.getMessage(), null)
            );
        } catch (Exception e) {
            log.error("평가 상태 조회 중 예외 발생", e);
            return ResponseEntity.internalServerError().body(
                    new ApiResponse<>("5000", "평가 상태 조회 중 오류가 발생했습니다", null)
            );
        }
    }

    /**
     * AI 평가 재실행 (JWT 필요)
     * POST /api/algo/evaluation/retry/{submissionId}
     */
    @PostMapping("/retry/{submissionId}")
    public ResponseEntity<ApiResponse<Void>> retryEvaluation(
            @PathVariable("submissionId") Long submissionId,
            @AuthenticationPrincipal JwtAuthentication authentication) {

        Long userId = extractUserId(authentication);

        log.info("AI 평가 재실행 요청 - submissionId: {}, userId: {}", submissionId, userId);

        try {
            // 비동기 재실행 시작
            CompletableFuture<Void> retryFuture = evaluationService.retryEvaluation(submissionId);

            // 즉시 응답 반환 (비동기 처리)
            return ResponseEntity.ok(
                    new ApiResponse<>("0000", "AI 평가 재실행을 시작했습니다", null)
            );

        } catch (IllegalArgumentException e) {
            log.warn("AI 평가 재실행 실패 - submissionId: {}, error: {}", submissionId, e.getMessage());
            return ResponseEntity.badRequest().body(
                    new ApiResponse<>("4000", e.getMessage(), null)
            );
        } catch (Exception e) {
            log.error("AI 평가 재실행 중 예외 발생", e);
            return ResponseEntity.internalServerError().body(
                    new ApiResponse<>("5000", "AI 평가 재실행 중 오류가 발생했습니다", null)
            );
        }
    }

    /**
     * 🧪 평가 상태 조회 테스트 (JWT 없이)
     * GET /api/algo/evaluation/test/status/{submissionId}
     */
    @GetMapping("/test/status/{submissionId}")
    public ResponseEntity<ApiResponse<AlgorithmEvaluationService.EvaluationStatusDto>> testGetEvaluationStatus(
            @PathVariable("submissionId") Long submissionId,
            @RequestParam(defaultValue = "1") Long userId) {

        log.info("평가 상태 조회 테스트 - submissionId: {}, userId: {}", submissionId, userId);

        try {
            AlgorithmEvaluationService.EvaluationStatusDto status =
                    evaluationService.getEvaluationStatus(submissionId);

            return ResponseEntity.ok(
                    new ApiResponse<>("0000", "평가 상태 조회 테스트 완료", status)
            );

        } catch (Exception e) {
            log.error("평가 상태 조회 테스트 실패", e);
            return ResponseEntity.badRequest().body(
                    new ApiResponse<>("T400", "테스트 실패: " + e.getMessage(), null)
            );
        }
    }

    /**
     * 🧪 AI 평가 재실행 테스트 (JWT 없이)
     * POST /api/algo/evaluation/test/retry/{submissionId}
     */
    @PostMapping("/test/retry/{submissionId}")
    public ResponseEntity<ApiResponse<Void>> testRetryEvaluation(
            @PathVariable("submissionId") Long submissionId,
            @RequestParam(defaultValue = "1") Long userId) {

        log.info("AI 평가 재실행 테스트 - submissionId: {}, userId: {}", submissionId, userId);

        try {
            // 비동기 재실행 시작
            CompletableFuture<Void> retryFuture = evaluationService.retryEvaluation(submissionId);

            return ResponseEntity.ok(
                    new ApiResponse<>("0000", "AI 평가 재실행 테스트 시작됨", null)
            );

        } catch (Exception e) {
            log.error("AI 평가 재실행 테스트 실패", e);
            return ResponseEntity.badRequest().body(
                    new ApiResponse<>("T401", "테스트 실패: " + e.getMessage(), null)
            );
        }
    }

    /**
     * 🧪 통합 플로우 모니터링 (JWT 없이)
     * GET /api/algo/evaluation/test/monitor/{submissionId}
     */
    @GetMapping("/test/monitor/{submissionId}")
    public ResponseEntity<ApiResponse<Object>> testMonitorEvaluation(
            @PathVariable("submissionId") Long submissionId) {

        log.info("통합 플로우 모니터링 - submissionId: {}", submissionId);

        try {
            // 평가 상태 조회
            AlgorithmEvaluationService.EvaluationStatusDto evaluationStatus =
                    evaluationService.getEvaluationStatus(submissionId);

            // 모니터링 정보 구성
            java.util.Map<String, Object> monitoringData = new java.util.HashMap<>();
            monitoringData.put("submissionId", submissionId);
            monitoringData.put("evaluationStatus", evaluationStatus);
            monitoringData.put("timestamp", java.time.LocalDateTime.now());

            // 상태에 따른 메시지
            String statusMessage = switch (evaluationStatus.getAiFeedbackStatus()) {
                case "PENDING" -> "AI 평가 대기 중";
                case "COMPLETED" -> "AI 평가 완료";
                case "FAILED" -> "AI 평가 실패";
                default -> "알 수 없는 상태";
            };

            monitoringData.put("statusMessage", statusMessage);
            monitoringData.put("isCompleted", "COMPLETED".equals(evaluationStatus.getAiFeedbackStatus()));

            return ResponseEntity.ok(
                    new ApiResponse<>("0000", "통합 플로우 모니터링 완료", monitoringData)
            );

        } catch (Exception e) {
            log.error("통합 플로우 모니터링 실패", e);
            return ResponseEntity.badRequest().body(
                    new ApiResponse<>("T402", "모니터링 실패: " + e.getMessage(), null)
            );
        }
    }

    /**
     * 🚀 평가 서비스 헬스 체크
     * GET /api/algo/evaluation/health
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> healthCheck() {
        try {
            log.info("평가 서비스 헬스 체크");

            String message = "AlgorithmEvaluationService 정상 동작 중";
            return ResponseEntity.ok(new ApiResponse<>("0000", message, message));

        } catch (Exception e) {
            log.error("평가 서비스 헬스 체크 실패", e);
            return ResponseEntity.internalServerError().body(
                    new ApiResponse<>("9999", "평가 서비스 상태 확인 중 오류가 발생했습니다", null)
            );
        }
    }
}