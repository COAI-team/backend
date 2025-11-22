package kr.or.kosa.backend.algorithm.controller;

import kr.or.kosa.backend.algorithm.domain.AlgoProblem;
import kr.or.kosa.backend.algorithm.dto.ProblemGenerationRequestDto;
import kr.or.kosa.backend.algorithm.dto.ProblemGenerationResponseDto;
import kr.or.kosa.backend.algorithm.service.AIProblemGeneratorService;
import kr.or.kosa.backend.algorithm.service.AlgorithmProblemService;
import kr.or.kosa.backend.commons.response.ApiResponse;
import kr.or.kosa.backend.security.jwt.JwtAuthentication;
import kr.or.kosa.backend.security.jwt.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 알고리즘 문제 컨트롤러 - Phase 1-3
 * 기본 조회 API + AI 문제 생성 API
 */
@RestController
@RequestMapping("/api/algo/problems")
@RequiredArgsConstructor
@Slf4j
public class AlgorithmProblemController {

    private final AlgorithmProblemService algorithmProblemService;
    private final AIProblemGeneratorService aiProblemGeneratorService;

    /**
     * AI 문제 생성 및 DB 저장
     * POST /api/algo/problems/generate
     */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateProblem(
            @RequestBody ProblemGenerationRequestDto request,
            @AuthenticationPrincipal JwtAuthentication authentication) {

        log.info("AI 문제 생성 요청 - 난이도: {}, 주제: {}", request.getDifficulty(), request.getTopic());

        try {
            // 1. 요청 데이터 검증
            if (request.getDifficulty() == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("4001", "난이도를 선택해주세요"));
            }

            if (request.getTopic() == null || request.getTopic().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("4002", "문제 주제를 입력해주세요"));
            }

            // 2. AI 문제 생성
            ProblemGenerationResponseDto aiResponse = aiProblemGeneratorService.generateProblem(request);

            // 3. 생성 결과 확인
            if (aiResponse.getStatus() != ProblemGenerationResponseDto.GenerationStatus.SUCCESS) {
                log.error("AI 문제 생성 실패 - 상태: {}, 에러: {}",
                        aiResponse.getStatus(), aiResponse.getErrorMessage());

                return ResponseEntity.internalServerError()
                        .body(ApiResponse.error("5001",
                                "AI 문제 생성에 실패했습니다: " + aiResponse.getErrorMessage()));
            }

            // 4. userId 추출 (JwtAuthentication → JwtUserDetails → id) 추후 수정 필요
            Long userId = null;
            if (authentication != null) {
                JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
                userId = userDetails.id().longValue(); // Integer → Long 변환
                log.debug("인증된 사용자 - userId: {}, email: {}", userId, userDetails.email());
            } else {
                log.debug("인증되지 않은 사용자 - 익명으로 문제 생성");
            }

            // 5. DB에 저장
            Long problemId = algorithmProblemService.saveGeneratedProblem(aiResponse, userId);

            log.info("AI 문제 생성 및 저장 완료 - 문제 ID: {}, 제목: {}, 생성자 ID: {}, 소요시간: {}초",
                    problemId,
                    aiResponse.getProblem().getAlgoProblemTitle(),
                    userId != null ? userId : "익명",
                    aiResponse.getGenerationTime());

            // 6. 응답 데이터 구성
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("problemId", problemId);
            responseData.put("title", aiResponse.getProblem().getAlgoProblemTitle());
            responseData.put("description", aiResponse.getProblem().getAlgoProblemDescription());
            responseData.put("difficulty", aiResponse.getProblem().getAlgoProblemDifficulty());
            responseData.put("testCaseCount", aiResponse.getTestCases() != null ? aiResponse.getTestCases().size() : 0);
            responseData.put("generationTime", aiResponse.getGenerationTime());

            return ResponseEntity.ok(ApiResponse.success(responseData));

        } catch (Exception e) {
            log.error("AI 문제 생성 중 예외 발생", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("5000", "문제 생성 중 서버 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 문제 목록 조회 (페이징)
     * GET /api/algo/problems?page=1&size=10
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProblems(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("알고리즘 문제 목록 조회 - page: {}, size: {}", page, size);

        try {
            // 페이지 번호 검증 (1부터 시작)
            if (page < 1) {
                page = 1;
            }
            if (size < 1 || size > 100) {
                size = 10;
            }

            // offset 계산 (0부터 시작)
            int offset = (page - 1) * size;

            // 데이터 조회
            List<AlgoProblem> problems = algorithmProblemService.getProblems(offset, size);
            int totalCount = algorithmProblemService.getTotalProblemsCount();

            // 페이징 정보 계산
            int totalPages = (int) Math.ceil((double) totalCount / size);
            boolean hasNext = page < totalPages;
            boolean hasPrevious = page > 1;

            // 응답 데이터 구성
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("problems", problems);
            responseData.put("currentPage", page);
            responseData.put("pageSize", size);
            responseData.put("totalCount", totalCount);
            responseData.put("totalPages", totalPages);
            responseData.put("hasNext", hasNext);
            responseData.put("hasPrevious", hasPrevious);

            log.info("문제 목록 조회 성공 - 조회된 문제 수: {}", problems.size());

            return ResponseEntity.ok(ApiResponse.success(responseData));

        } catch (Exception e) {
            log.error("문제 목록 조회 실패", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("9999", "문제 목록 조회 중 오류가 발생했습니다."));
        }
    }

    /**
     * 문제 상세 조회
     * GET /api/algo/problems/{problemId}
     */
    @GetMapping("/{problemId}")
    public ResponseEntity<ApiResponse<AlgoProblem>> getProblemDetail(@PathVariable Long problemId) {

        log.info("알고리즘 문제 상세 조회 - problemId: {}", problemId);

        try {
            // 문제 존재 여부 확인
            if (!algorithmProblemService.existsProblem(problemId)) {
                log.warn("존재하지 않는 문제 조회 시도 - problemId: {}", problemId);
                return ResponseEntity.notFound().build();
            }

            // 문제 상세 정보 조회
            AlgoProblem problem = algorithmProblemService.getProblemDetail(problemId);

            log.info("문제 상세 조회 성공 - problemId: {}, title: {}", problemId, problem.getAlgoProblemTitle());

            return ResponseEntity.ok(ApiResponse.success(problem));

        } catch (Exception e) {
            log.error("문제 상세 조회 실패 - problemId: {}", problemId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("9999", "문제 상세 조회 중 오류가 발생했습니다."));
        }
    }

    /**
     * 문제 존재 여부 확인
     * HEAD /api/algo/problems/{problemId}
     */
    @RequestMapping(value = "/{problemId}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> checkProblemExists(@PathVariable Long problemId) {

        log.info("문제 존재 여부 확인 - problemId: {}", problemId);

        try {
            boolean exists = algorithmProblemService.existsProblem(problemId);

            if (exists) {
                log.info("문제 존재 확인 - problemId: {}", problemId);
                return ResponseEntity.ok().build();
            } else {
                log.info("문제 없음 확인 - problemId: {}", problemId);
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            log.error("문제 존재 여부 확인 실패 - problemId: {}", problemId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 서버 상태 확인용 (헬스 체크)
     * GET /api/algo/problems/health
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> healthCheck() {

        try {
            int count = algorithmProblemService.getTotalProblemsCount();
            String message = String.format("알고리즘 서비스 정상 동작 중 (총 문제 수: %d)", count);

            return ResponseEntity.ok(ApiResponse.success(message));

        } catch (Exception e) {
            log.error("헬스 체크 실패", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("9999", "서비스 상태 확인 중 오류가 발생했습니다."));
        }
    }
}