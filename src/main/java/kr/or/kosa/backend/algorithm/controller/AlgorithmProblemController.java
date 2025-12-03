package kr.or.kosa.backend.algorithm.controller;

import kr.or.kosa.backend.algorithm.domain.AlgoProblem;
import kr.or.kosa.backend.algorithm.dto.*;
import kr.or.kosa.backend.algorithm.exception.AlgoErrorCode;
import kr.or.kosa.backend.algorithm.service.AIProblemGeneratorService;
import kr.or.kosa.backend.algorithm.service.AlgorithmProblemService;
import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
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
 * 알고리즘 문제 컨트롤러
 */
@RestController
@RequestMapping("/algo/problems")
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
            if (request.getDifficulty() == null) {
                throw new CustomBusinessException(AlgoErrorCode.INVALID_INPUT);
            }

            if (request.getTopic() == null) {
                throw new CustomBusinessException(AlgoErrorCode.INVALID_INPUT);
            }

            ProblemGenerationResponseDto aiResponse = aiProblemGeneratorService.generateProblem(request);

            if (aiResponse.getStatus() != ProblemGenerationResponseDto.GenerationStatus.SUCCESS) {
                log.error("AI 문제 생성 실패 - 상태: {}, 에러: {}",
                        aiResponse.getStatus(), aiResponse.getErrorMessage());
                throw new CustomBusinessException(AlgoErrorCode.PROBLEM_GENERATION_FAIL);
            }

            Long userId = null;
            if (authentication != null) {
                JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
                userId = userDetails.id().longValue();
                log.debug("인증된 사용자 - userId: {}, email: {}", userId, userDetails.email());
            } else {
                log.debug("인증되지 않은 사용자 - 익명으로 문제 생성");
            }

            Long problemId = algorithmProblemService.saveGeneratedProblem(aiResponse, userId);

            log.info("AI 문제 생성 및 저장 완료 - 문제 ID: {}, 제목: {}, 생성자 ID: {}, 소요시간: {}초",
                    problemId,
                    aiResponse.getProblem().getAlgoProblemTitle(),
                    userId != null ? userId : "익명",
                    aiResponse.getGenerationTime());

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("problemId", problemId);
            responseData.put("title", aiResponse.getProblem().getAlgoProblemTitle());
            responseData.put("description", aiResponse.getProblem().getAlgoProblemDescription());
            responseData.put("difficulty", aiResponse.getProblem().getAlgoProblemDifficulty());
            responseData.put("testCaseCount", aiResponse.getTestCases() != null ? aiResponse.getTestCases().size() : 0);
            responseData.put("generationTime", aiResponse.getGenerationTime());

            return ResponseEntity.ok(ApiResponse.success(responseData));

        } catch (CustomBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 문제 생성 중 예외 발생", e);
            throw new CustomBusinessException(AlgoErrorCode.PROBLEM_SAVE_FAIL);
        }
    }

    /**
     * 문제 목록 조회 (기존 - 하위 호환성 유지)
     * GET /api/algo/problems?page=1&size=10&difficulty=BRONZE&source=AI_GENERATED&keyword=검색어&topic=배열
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProblems(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String topic) {  // topic 파라미터 추가

        log.info("========================================");
        log.info("문제 목록 조회 요청");
        log.info("page: {}, size: {}", page, size);
        log.info("difficulty: '{}' (null: {})", difficulty, difficulty == null);
        log.info("source: '{}' (null: {})", source, source == null);
        log.info("keyword: '{}' (null: {})", keyword, keyword == null);
        log.info("topic: '{}' (null: {})", topic, topic == null);  // topic 로그 추가
        log.info("========================================");

        try {
            if (page < 1) {
                page = 1;
            }
            if (size < 1 || size > 100) {
                size = 10;
            }

            // 빈 문자열 처리
            if (difficulty != null && difficulty.trim().isEmpty()) {
                difficulty = null;
                log.info("difficulty를 null로 변환");
            }
            if (source != null && source.trim().isEmpty()) {
                source = null;
                log.info("source를 null로 변환");
            }
            if (keyword != null && keyword.trim().isEmpty()) {
                keyword = null;
                log.info("keyword를 null로 변환");
            }
            // topic 빈 문자열 처리 추가
            if (topic != null && topic.trim().isEmpty()) {
                topic = null;
                log.info("topic을 null로 변환");
            }

            int offset = (page - 1) * size;

            log.info("Service 호출 전 - offset: {}, limit: {}, difficulty: {}, source: {}, keyword: {}, topic: {}",
                    offset, size, difficulty, source, keyword, topic);

            // Service 호출에 topic 추가
            List<AlgoProblem> problems = algorithmProblemService.getProblemsWithFilter(
                    offset, size, difficulty, source, keyword, topic);

            log.info("Service 호출 후 - 조회된 문제 수: {}", problems.size());

            // getTotalProblemsCountWithFilter에도 topic 추가
            int totalCount = algorithmProblemService.getTotalProblemsCountWithFilter(
                    difficulty, source, keyword, topic);

            int totalPages = (int) Math.ceil((double) totalCount / size);
            boolean hasNext = page < totalPages;
            boolean hasPrevious = page > 1;

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
            log.error("❌❌❌ 문제 목록 조회 실패 ❌❌❌", e);
            log.error("에러 타입: {}", e.getClass().getName());
            log.error("에러 메시지: {}", e.getMessage());
            throw new CustomBusinessException(AlgoErrorCode.INVALID_INPUT);
        }
    }

    /**
     * 문제 목록 조회 V2 (고급 필터링 + 통계)
     * GET /api/algo/problems/v2?page=1&size=10&difficulty=BRONZE&source=AI_GENERATED&language=Java&status=solved&sortBy=latest&topic=배열
     */
    @GetMapping("/v2")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProblemsV2(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String topic,  // topic 파라미터 추가
            @RequestParam(defaultValue = "latest") String sortBy,
            @AuthenticationPrincipal JwtAuthentication authentication) {

        log.info("문제 목록 조회 V2 - page: {}, size: {}, difficulty: {}, source: {}, language: {}, status: {}, keyword: {}, topic: {}, sortBy: {}",
                page, size, difficulty, source, language, status, keyword, topic, sortBy);

        try {
            // 사용자 ID 추출
            Long userId = null;
            if (authentication != null) {
                JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
                userId = userDetails.id().longValue();
            }

            // 요청 Dto 생성
            ProblemListRequestDto request = ProblemListRequestDto.builder()
                    .page(page)
                    .size(size)
                    .difficulty(difficulty)
                    .source(source)
                    .language(language)
                    .status(status)
                    .keyword(keyword)
                    .topic(topic)
                    .sortBy(sortBy)
                    .userId(userId)
                    .build();

            // 문제 목록 조회
            Map<String, Object> responseData = algorithmProblemService.getProblemListWithStats(request);

            log.info("문제 목록 조회 V2 성공 - 총 {}개", responseData.get("totalCount"));

            return ResponseEntity.ok(ApiResponse.success(responseData));

        } catch (Exception e) {
            log.error("문제 목록 조회 V2 실패", e);
            throw new CustomBusinessException(AlgoErrorCode.INVALID_INPUT);
        }
    }

    /**
     * 통계 정보 조회
     * GET /api/algo/problems/statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<ProblemStatisticsDto>> getStatistics(
            @AuthenticationPrincipal JwtAuthentication authentication) {

        log.info("통계 정보 조회");

        try {
            // 사용자 ID 추출
            Long userId = null;
            if (authentication != null) {
                JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
                userId = userDetails.id().longValue();
            }

            // 통계 조회
            ProblemStatisticsDto statistics = algorithmProblemService.getProblemStatistics(userId);

            log.info("통계 정보 조회 성공 - {}", statistics);

            return ResponseEntity.ok(ApiResponse.success(statistics));

        } catch (Exception e) {
            log.error("통계 정보 조회 실패", e);
            throw new CustomBusinessException(AlgoErrorCode.INVALID_INPUT);
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
            if (!algorithmProblemService.existsProblem(problemId)) {
                log.warn("존재하지 않는 문제 조회 시도 - problemId: {}", problemId);
                throw new CustomBusinessException(AlgoErrorCode.PROBLEM_NOT_FOUND);
            }

            AlgoProblem problem = algorithmProblemService.getProblemDetail(problemId);

            log.info("문제 상세 조회 성공 - problemId: {}, title: {}", problemId, problem.getAlgoProblemTitle());

            return ResponseEntity.ok(ApiResponse.success(problem));

        } catch (CustomBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("문제 상세 조회 실패 - problemId: {}", problemId, e);
            throw new CustomBusinessException(AlgoErrorCode.PROBLEM_NOT_FOUND);
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
            throw new CustomBusinessException(AlgoErrorCode.PROBLEM_NOT_FOUND);
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
            throw new CustomBusinessException(AlgoErrorCode.INVALID_INPUT);
        }
    }
}