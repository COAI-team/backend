package kr.or.kosa.backend.algorithm.controller;

import kr.or.kosa.backend.algorithm.dto.ProblemSolveResponseDto;
import kr.or.kosa.backend.algorithm.dto.SubmissionRequestDto;
import kr.or.kosa.backend.algorithm.dto.SubmissionResponseDto;
import kr.or.kosa.backend.algorithm.domain.ProgrammingLanguage;
import kr.or.kosa.backend.algorithm.service.AlgorithmSolvingService;
import kr.or.kosa.backend.commons.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 알고리즘 기능 테스트용 컨트롤러 (JWT 없이 테스트용)
 */
@RestController
@RequestMapping("/api/algo/test")
@RequiredArgsConstructor
@Slf4j
public class AlgorithmTestController {

    private final AlgorithmSolvingService solvingService;

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
}
