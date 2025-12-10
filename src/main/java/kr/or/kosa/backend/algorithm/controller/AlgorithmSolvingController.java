package kr.or.kosa.backend.algorithm.controller;

import kr.or.kosa.backend.algorithm.dto.request.SubmissionRequestDto;
import kr.or.kosa.backend.algorithm.dto.request.TestRunRequestDto;
import kr.or.kosa.backend.algorithm.dto.response.ProblemSolveResponseDto;
import kr.or.kosa.backend.algorithm.dto.response.SubmissionResponseDto;
import kr.or.kosa.backend.algorithm.dto.response.TestRunResponseDto;
import kr.or.kosa.backend.algorithm.exception.AlgoErrorCode;
import kr.or.kosa.backend.algorithm.service.AlgorithmSolvingService;
import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
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
     * SecurityContext에서 직접 사용자 ID 추출
     * @AuthenticationPrincipal이 JwtUserDetails를 JwtAuthentication으로 캐스팅 실패하므로
     * SecurityContextHolder에서 직접 Authentication을 가져옴
     */
    private Long extractUserId(JwtAuthentication authentication) {
        // @AuthenticationPrincipal이 null인 경우 SecurityContextHolder에서 직접 조회
        org.springframework.security.core.Authentication auth = authentication;
        if (auth == null) {
            auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
        }

        if (auth == null) {
            log.warn("❌ 인증 정보가 없습니다.");
            throw new CustomBusinessException(AlgoErrorCode.LOGIN_REQUIRED);
        }

        Object principal = auth.getPrincipal();
        if (!(principal instanceof JwtUserDetails userDetails)) {
            log.warn("❌ 유효하지 않은 인증 정보입니다: {}", principal);
            throw new CustomBusinessException(AlgoErrorCode.LOGIN_REQUIRED);
        }

        Long userId = userDetails.id().longValue();
        log.debug("✅ 인증된 사용자 - userId: {}", userId);
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
            throw new CustomBusinessException(AlgoErrorCode.PROBLEM_NOT_FOUND);
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
            throw new CustomBusinessException(AlgoErrorCode.INVALID_INPUT);

        } catch (Exception e) {
            log.error("코드 제출 중 예외 발생", e);
            throw new CustomBusinessException(AlgoErrorCode.SUBMISSION_SAVE_FAIL);
        }
    }

    /**
     * 샘플 테스트 실행 (제출 없이 코드만 실행)
     * POST /api/algo/submissions/test
     *
     * - 샘플 테스트케이스(isSample=true)만 실행
     * - DB에 저장하지 않음
     * - AI 평가 없음
     * - 프론트엔드의 "코드 실행" 버튼에서 호출
     */
    @PostMapping("/submissions/test")
    public ResponseEntity<ApiResponse<TestRunResponseDto>> runSampleTest(
            @RequestBody @Valid TestRunRequestDto request,
            @AuthenticationPrincipal JwtAuthentication authentication) {

        Long userId = extractUserId(authentication);

        log.info("샘플 테스트 실행 요청 - problemId: {}, language: {}, userId: {}",
                request.getProblemId(), request.getLanguage(), userId);

        try {
            TestRunResponseDto response = solvingService.runSampleTest(request);

            return ResponseEntity.ok(
                    new ApiResponse<>("0000", "테스트 실행 완료", response)
            );

        } catch (IllegalArgumentException e) {
            log.warn("샘플 테스트 실행 실패 - error: {}", e.getMessage());
            throw new CustomBusinessException(AlgoErrorCode.INVALID_INPUT);

        } catch (Exception e) {
            log.error("샘플 테스트 실행 중 예외 발생", e);
            throw new CustomBusinessException(AlgoErrorCode.TEST_RUN_FAILED);
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
            throw new CustomBusinessException(AlgoErrorCode.SUBMISSION_NOT_FOUND);
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
            throw new CustomBusinessException(AlgoErrorCode.SUBMISSION_UPDATE_FAIL);
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
            throw new CustomBusinessException(AlgoErrorCode.SUBMISSION_NOT_FOUND);
        }
    }
}
