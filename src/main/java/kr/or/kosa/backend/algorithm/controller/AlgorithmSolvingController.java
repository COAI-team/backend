package kr.or.kosa.backend.algorithm.controller;

import kr.or.kosa.backend.algorithm.dto.ProblemSolveResponseDto;
import kr.or.kosa.backend.algorithm.dto.SubmissionRequestDto;
import kr.or.kosa.backend.algorithm.dto.SubmissionResponseDto;
import kr.or.kosa.backend.algorithm.service.AlgorithmSolvingService;
import kr.or.kosa.backend.commons.response.ApiResponse;
import kr.or.kosa.backend.security.jwt.JwtAuthentication;
import kr.or.kosa.backend.security.jwt.JwtUserDetails;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/algo")
@RequiredArgsConstructor
@Slf4j
public class AlgorithmSolvingController {

    private final AlgorithmSolvingService solvingService;

    /**
     * AlgorithmProblemController와 동일한 방식의 userId 추출
     */
    private Long extractUserId(JwtAuthentication authentication) {
        if (authentication == null) {
            log.error("❌ JwtAuthentication이 null입니다. 인증 불가");
            throw new IllegalStateException("인증 정보가 없습니다.");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof JwtUserDetails userDetails)) {
            log.error("❌ principal이 JwtUserDetails가 아닙니다: {}", principal);
            throw new IllegalStateException("JWT 사용자 정보가 올바르지 않습니다.");
        }

        Long userId = userDetails.id().longValue();
        log.debug("✔ 인증된 사용자 - userId: {}", userId);
        return userId;
    }

    /**
     * 문제 풀이 시작 (ALG-04)
     */
    @GetMapping("/problems/{problemId}/solve")
    public ResponseEntity<ApiResponse<ProblemSolveResponseDto>> startProblemSolving(
            @PathVariable("problemId") Long problemId,
            @AuthenticationPrincipal JwtAuthentication authentication) {

        Long userId = extractUserId(authentication);

        log.info("문제 풀이 시작 요청 - problemId: {}, userId: {}", problemId, userId);

        try {
            ProblemSolveResponseDto response =
                    solvingService.startProblemSolving(problemId, userId);

            return ResponseEntity.ok(
                    new ApiResponse<>("0000", "문제 풀이를 시작합니다", response)
            );

        } catch (IllegalArgumentException e) {
            log.warn("문제 풀이 시작 실패 - problemId: {}, error: {}", problemId, e.getMessage());
            return ResponseEntity.badRequest().body(
                    new ApiResponse<>("4000", e.getMessage(), null)
            );
        }
    }

    /**
     * 코드 제출 및 채점 (ALG-07)
     */
    @PostMapping("/submissions")
    public ResponseEntity<ApiResponse<SubmissionResponseDto>> submitCode(
            @RequestBody @Valid SubmissionRequestDto request,
            @AuthenticationPrincipal JwtAuthentication authentication) {

        Long userId = extractUserId(authentication);

        log.info("코드 제출 요청 - problemId: {}, userId: {}, language: {}",
                request.getProblemId(), userId, request.getLanguage());

        try {
            SubmissionResponseDto response =
                    solvingService.submitCode(request, userId);

            return ResponseEntity.ok(
                    new ApiResponse<>("0000", "코드 제출이 완료되었습니다. 채점 중입니다...", response)
            );

        } catch (IllegalArgumentException e) {
            log.warn("코드 제출 실패 - error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    new ApiResponse<>("4000", e.getMessage(), null)
            );

        } catch (Exception e) {
            log.error("코드 제출 중 예외 발생", e);
            return ResponseEntity.internalServerError().body(
                    new ApiResponse<>("5000", "코드 제출 처리 중 오류가 발생했습니다", null)
            );
        }
    }

    /**
     * 제출 결과 조회
     */
    @GetMapping("/submissions/{submissionId}")
    public ResponseEntity<ApiResponse<SubmissionResponseDto>> getSubmissionResult(
            @PathVariable("submissionId") Long submissionId,
            @AuthenticationPrincipal JwtAuthentication authentication) {

        Long userId = extractUserId(authentication);

        log.info("제출 결과 조회 - submissionId: {}, userId: {}", submissionId, userId);

        try {
            SubmissionResponseDto response =
                    solvingService.getSubmissionResult(submissionId, userId);

            return ResponseEntity.ok(
                    new ApiResponse<>("0000", "제출 결과 조회 완료", response)
            );

        } catch (IllegalArgumentException e) {
            log.warn("제출 결과 조회 실패 - submissionId: {}, error: {}", submissionId, e.getMessage());
            return ResponseEntity.badRequest().body(
                    new ApiResponse<>("4000", e.getMessage(), null)
            );
        }
    }

    /**
     * 제출 결과 공개/비공개 설정 (ALG-09)
     */
    @PatchMapping("/submissions/{submissionId}/visibility")
    public ResponseEntity<ApiResponse<Void>> updateSharingStatus(
            @PathVariable("submissionId") Long submissionId,
            @RequestParam("isShared") Boolean isShared,
            @AuthenticationPrincipal JwtAuthentication authentication) {

        Long userId = extractUserId(authentication);

        log.info("제출 공개 설정 변경 - submissionId: {}, isShared: {}, userId: {}",
                submissionId, isShared, userId);

        try {
            solvingService.updateSharingStatus(submissionId, isShared, userId);

            String message = isShared
                    ? "제출 결과를 공개했습니다"
                    : "제출 결과를 비공개로 설정했습니다";

            return ResponseEntity.ok(new ApiResponse<>("0000", message, null));

        } catch (IllegalArgumentException e) {
            log.warn("제출 공개 설정 실패 - error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    new ApiResponse<>("4000", e.getMessage(), null)
            );
        }
    }

    /**
     * 사용자 제출 이력 조회 (ALG-11)
     */
    @GetMapping("/submissions/my")
    public ResponseEntity<ApiResponse<java.util.List<SubmissionResponseDto>>> getMySubmissions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal JwtAuthentication authentication) {

        Long userId = extractUserId(authentication);

        log.info("내 제출 이력 조회 - userId: {}, page: {}, size: {}", userId, page, size);

        try {
            java.util.List<SubmissionResponseDto> response =
                    solvingService.getUserSubmissions(userId, page, size);

            return ResponseEntity.ok(new ApiResponse<>("0000", "제출 이력 조회 완료", response));

        } catch (Exception e) {
            log.error("제출 이력 조회 중 예외 발생", e);
            return ResponseEntity.internalServerError().body(
                    new ApiResponse<>("5000", "제출 이력 조회 중 오류가 발생했습니다", null)
            );
        }
    }
}
