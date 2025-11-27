package kr.or.kosa.backend.algorithm.controller;

import kr.or.kosa.backend.algorithm.dto.SubmissionAiStatusDto;
import kr.or.kosa.backend.algorithm.exception.AlgoErrorCode;
import kr.or.kosa.backend.algorithm.service.AlgorithmEvaluationService;
import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import kr.or.kosa.backend.commons.response.ApiResponse;
import kr.or.kosa.backend.security.jwt.JwtAuthentication;
import kr.or.kosa.backend.security.jwt.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/algo/evaluation")
@RequiredArgsConstructor
@Slf4j
public class AlgorithmEvaluationController {

    private final AlgorithmEvaluationService evaluationService;

    /**
     * JWT에서 userId 추출
     */
//    private Long extractUserId(JwtAuthentication authentication) {
//        if (authentication == null) {
//            throw new IllegalStateException("인증 정보가 없습니다.");
//        }
//
//        Object principal = authentication.getPrincipal();
//        if (!(principal instanceof JwtUserDetails userDetails)) {
//            throw new IllegalStateException("JWT 사용자 정보가 올바르지 않습니다.");
//        }
//        return userDetails.id().longValue();
//    }
    // 테스트용
    private Long extractUserId(JwtAuthentication authentication) {
        if (authentication == null) {
            log.warn("🧪 테스트 모드: authentication이 null이므로 기본 userId=1 사용");
            return 1L;  // ✅ 예외 대신 기본값 반환
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof JwtUserDetails userDetails)) {
            log.warn("🧪 테스트 모드: principal이 JwtUserDetails가 아니므로 기본 userId=1 사용");
            return 1L;  // ✅ 예외 대신 기본값 반환
        }

        Long userId = userDetails.id().longValue();
        log.debug("✅ 인증된 사용자 - userId: {}", userId);
        return userId;
    }

    /**
     * 평가 상태 조회
     * GET /api/algo/evaluation/status/{submissionId}
     */
    @GetMapping("/status/{submissionId}")
    public ResponseEntity<ApiResponse<SubmissionAiStatusDto>> getEvaluationStatus(
            @PathVariable Long submissionId,
            @AuthenticationPrincipal JwtAuthentication authentication
    ) {

        Long userId = extractUserId(authentication);
        log.info("평가 상태 조회 요청 - submissionId: {}, userId: {}", submissionId, userId);

        try {
            SubmissionAiStatusDto status = evaluationService.getEvaluationStatus(submissionId);

            return ResponseEntity.ok(ApiResponse.success(status));

        } catch (IllegalArgumentException e) {
            throw new CustomBusinessException(AlgoErrorCode.INVALID_INPUT);
        } catch (Exception e) {
            log.error("평가 상태 조회 중 오류 발생", e);
            throw new CustomBusinessException(AlgoErrorCode.EVALUATION_PROCESSING_ERROR);
        }
    }

    /**
     * AI 평가 재실행
     * POST /api/algo/evaluation/retry/{submissionId}
     */
    @PostMapping("/retry/{submissionId}")
    public ResponseEntity<ApiResponse<Void>> retryEvaluation(
            @PathVariable Long submissionId,
            @AuthenticationPrincipal JwtAuthentication authentication
    ) {

        Long userId = extractUserId(authentication);
        log.info("AI 평가 재실행 요청 - submissionId: {}, userId: {}", submissionId, userId);

        try {
            CompletableFuture<Void> future = evaluationService.retryEvaluation(submissionId);

            return ResponseEntity.ok(
                    new ApiResponse<>("0000", "평가 재실행 요청 완료", null)
            );

        } catch (Exception e) {
            log.error("AI 평가 재실행 중 오류 발생", e);
            throw new CustomBusinessException(AlgoErrorCode.EVALUATION_RETRY_FAIL);
        }
    }
}