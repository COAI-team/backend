package kr.or.kosa.backend.algorithm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.algorithm.dto.PoolStatusDto;
import kr.or.kosa.backend.algorithm.dto.enums.UsageType;
import kr.or.kosa.backend.algorithm.dto.response.ProblemGenerationResponseDto;
import kr.or.kosa.backend.algorithm.exception.AlgoErrorCode;
import kr.or.kosa.backend.algorithm.service.DailyMissionService;
import kr.or.kosa.backend.algorithm.service.ProblemGenerationOrchestrator;
import kr.or.kosa.backend.algorithm.service.ProblemPoolService;
import kr.or.kosa.backend.algorithm.service.RateLimitService;
import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import kr.or.kosa.backend.commons.response.ApiResponse;
import kr.or.kosa.backend.security.jwt.JwtAuthentication;
import kr.or.kosa.backend.security.jwt.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 문제 풀 컨트롤러
 * <p>풀에서 문제 소비, 상태 조회, 수동 채우기 API 제공
 */
@RestController
@RequestMapping("/algo/pool")
@RequiredArgsConstructor
@Slf4j
public class ProblemPoolController {

    private final ProblemPoolService poolService;
    private final ObjectMapper objectMapper;
    private final RateLimitService rateLimitService;
    private final DailyMissionService dailyMissionService;

    /**
     * SecurityContext에서 사용자 ID 추출
     * <p>@AuthenticationPrincipal이 null인 경우 SecurityContextHolder에서 직접 조회
     *
     * @param authentication 인증 정보 (nullable)
     * @return 사용자 ID, 비로그인 시 null
     */
    private Long extractUserId(JwtAuthentication authentication) {
        org.springframework.security.core.Authentication auth = authentication;
        if (auth == null) {
            auth = SecurityContextHolder.getContext().getAuthentication();
        }

        if (auth == null || auth.getPrincipal() == null) {
            log.debug("인증 정보 없음 - 비로그인 사용자");
            return null;
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof JwtUserDetails userDetails) {
            Long userId = userDetails.id().longValue();
            log.debug("✅ 인증된 사용자 - userId: {}", userId);
            return userId;
        }

        log.debug("유효하지 않은 인증 정보 - principal: {}", principal.getClass().getSimpleName());
        return null;
    }

    /**
     * 현재 활성화된 테마 목록 (프론트엔드와 동기화 필요)
     */
    private static final List<String> ACTIVE_THEMES = List.of(
            "SANTA_DELIVERY",
            "SNOWBALL_FIGHT",
            "CHRISTMAS_TREE",
            "NEW_YEAR_FIREWORKS",
            "SKI_RESORT"
    );

    /**
     * 풀에서 문제 꺼내기 (SSE 스트리밍)
     * <p>풀에 문제가 있으면 즉시 반환, 없으면 실시간 생성
     * <p>SSE는 Authorization 헤더를 지원하지 않으므로 userId를 쿼리 파라미터로 받음
     * <p>Rate Limit은 인터셉터가 아닌 이 메서드에서 직접 처리 (SSE 재연결 중복 방지)
     *
     * GET /api/algo/pool/draw/stream?difficulty=GOLD&topic=DFS/BFS&theme=SANTA_DELIVERY&userId=3
     */
    @GetMapping(value = "/draw/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> drawProblemStream(
            @RequestParam String difficulty,
            @RequestParam String topic,
            @RequestParam String theme,
            @RequestParam(required = false) Long userId) {

        log.info("풀에서 문제 꺼내기 요청 - difficulty: {}, topic: {}, theme: {}, userId: {}",
                difficulty, topic, theme, userId);

        // 1. userId 필수 체크
        if (userId == null) {
            log.warn("SSE 요청 - userId 없음 (로그인 필요)");
            return createErrorFlux("로그인이 필요합니다.");
        }

        // 2. 진행 중인 요청 체크 (SSE 재연결 중복 방지)
        if (rateLimitService.isInProgress(userId, UsageType.GENERATE)) {
            log.warn("SSE 재연결 감지 - 이미 진행 중인 요청 있음, userId: {}", userId);
            return createErrorFlux("이미 문제 생성이 진행 중입니다. 잠시 후 다시 시도해주세요.");
        }

        // 3. 구독자 여부 확인
        boolean isSubscriber = dailyMissionService.isSubscriber(userId);

        // 4. 사용량 체크 (증가 없이 체크만)
        RateLimitService.UsageCheckResult checkResult =
                rateLimitService.checkUsageOnly(userId, UsageType.GENERATE, isSubscriber);

        if (!checkResult.allowed()) {
            log.info("Rate limit 초과 - userId: {}, 사용량: {}/{}", userId, checkResult.currentUsage(), checkResult.dailyLimit());
            return createErrorFlux(checkResult.message());
        }

        // 5. 진행 중 마커 설정 (재연결 감지용)
        rateLimitService.setInProgress(userId, UsageType.GENERATE);
        log.debug("진행 중 마커 설정 완료 - userId: {}", userId);

        final Long finalUserId = userId;
        final boolean finalIsSubscriber = isSubscriber;

        return Flux.create(sink -> {
            reactor.core.scheduler.Schedulers.boundedElastic().schedule(() -> {
                // 요청 시작 시간 측정
                long requestStartTime = System.currentTimeMillis();
                final boolean[] fromPoolFlag = {false};  // 풀에서 가져왔는지 여부

                try {
                    // 풀에서 문제 꺼내기 (없으면 실시간 생성)
                    ProblemGenerationResponseDto response = poolService.drawProblem(
                            difficulty,
                            topic,
                            theme,
                            finalUserId,
                            progressEvent -> {
                                // 실시간 생성 시 진행률 전송 → 풀에서 가져온 게 아님
                                fromPoolFlag[0] = false;
                                try {
                                    Map<String, Object> event = new HashMap<>();
                                    event.put("type", "PROGRESS");
                                    event.put("status", progressEvent.getStatus());
                                    event.put("message", progressEvent.getMessage());
                                    event.put("percentage", progressEvent.getPercentage());
                                    sink.next("data: " + objectMapper.writeValueAsString(event) + "\n\n");
                                } catch (Exception e) {
                                    log.error("SSE 진행률 전송 실패", e);
                                }
                            }
                    );

                    // 6. 성공 시에만 사용량 증가 (구독자가 아닌 경우)
                    if (!finalIsSubscriber) {
                        rateLimitService.incrementUsageOnly(finalUserId, UsageType.GENERATE);
                    }

                    // 요청 처리 시간 측정 (fetchTime)
                    double fetchTime = (System.currentTimeMillis() - requestStartTime) / 1000.0;

                    // 풀에서 가져왔는지 판단
                    boolean fromPool = fetchTime < 3.0 && response.getGenerationTime() != null;

                    // 완료 이벤트 전송 (DB 필드 직접 매핑)
                    Map<String, Object> completeEvent = new HashMap<>();
                    completeEvent.put("type", "COMPLETE");
                    completeEvent.put("problemId", response.getProblemId());
                    completeEvent.put("title", response.getProblem().getAlgoProblemTitle());
                    completeEvent.put("description", response.getProblem().getAlgoProblemDescription());
                    completeEvent.put("inputFormat", response.getProblem().getInputFormat());
                    completeEvent.put("outputFormat", response.getProblem().getOutputFormat());
                    completeEvent.put("constraints", response.getProblem().getConstraints());
                    completeEvent.put("algoProblemTags", response.getProblem().getAlgoProblemTags());
                    completeEvent.put("testcases", response.getTestCases());
                    completeEvent.put("difficulty", response.getProblem().getAlgoProblemDifficulty().name());
                    completeEvent.put("testCaseCount", response.getTestCases() != null ? response.getTestCases().size() : 0);
                    completeEvent.put("generationTime", response.getGenerationTime());
                    completeEvent.put("fetchTime", fromPool ? fetchTime : null);
                    completeEvent.put("fromPool", fromPool);

                    sink.next("data: " + objectMapper.writeValueAsString(completeEvent) + "\n\n");
                    sink.complete();

                    log.info("문제 전달 완료 - problemId: {}, userId: {}, fromPool: {}, fetchTime: {}초",
                            response.getProblemId(), finalUserId, fromPool, fetchTime);

                    // 비동기로 풀 보충
                    refillPoolAsync(difficulty, topic, theme);

                } catch (Exception e) {
                    log.error("문제 꺼내기 실패 - userId: {}", finalUserId, e);
                    try {
                        Map<String, Object> errorEvent = new HashMap<>();
                        errorEvent.put("type", "ERROR");
                        errorEvent.put("message", e.getMessage());
                        sink.next("data: " + objectMapper.writeValueAsString(errorEvent) + "\n\n");
                    } catch (Exception ex) {
                        sink.next("data: {\"type\":\"ERROR\",\"message\":\"" + e.getMessage() + "\"}\n\n");
                    }
                    sink.complete();
                } finally {
                    // 7. 진행 중 마커 해제 (성공/실패 모두)
                    rateLimitService.clearInProgress(finalUserId, UsageType.GENERATE);
                    log.debug("진행 중 마커 해제 - userId: {}", finalUserId);
                }
            });
        });
    }

    /**
     * SSE 에러 응답 생성 헬퍼
     */
    private Flux<String> createErrorFlux(String message) {
        return Flux.just("data: {\"type\":\"ERROR\",\"message\":\"" + message + "\"}\n\n");
    }

    /**
     * 풀에서 문제 꺼내기 (동기 방식)
     * <p>SSE 지원이 어려운 환경용
     *
     * POST /api/algo/pool/draw
     */
    @PostMapping("/draw")
    public ResponseEntity<ApiResponse<Map<String, Object>>> drawProblem(
            @RequestParam String difficulty,
            @RequestParam String topic,
            @RequestParam String theme,
            @AuthenticationPrincipal JwtAuthentication authentication) {

        Long userId = extractUserId(authentication);
        log.info("풀에서 문제 꺼내기 요청 (동기) - difficulty: {}, topic: {}, theme: {}, userId: {}",
                difficulty, topic, theme, userId);

        try {
            ProblemGenerationResponseDto response = poolService.drawProblem(
                    difficulty, topic, theme, userId, null);

            // DB 필드 직접 매핑
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("problemId", response.getProblemId());
            responseData.put("title", response.getProblem().getAlgoProblemTitle());
            responseData.put("description", response.getProblem().getAlgoProblemDescription());
            responseData.put("inputFormat", response.getProblem().getInputFormat());  // DB의 INPUT_FORMAT 컬럼
            responseData.put("outputFormat", response.getProblem().getOutputFormat());  // DB의 OUTPUT_FORMAT 컬럼
            responseData.put("constraints", response.getProblem().getConstraints());  // DB의 CONSTRAINTS 컬럼
            responseData.put("algoProblemTags", response.getProblem().getAlgoProblemTags());  // DB의 ALGO_PROBLEM_TAGS 컬럼
            responseData.put("testcases", response.getTestCases());  // 테스트케이스 목록 (isSample 포함)
            responseData.put("difficulty", response.getProblem().getAlgoProblemDifficulty().name());
            responseData.put("testCaseCount", response.getTestCases() != null ? response.getTestCases().size() : 0);
            responseData.put("generationTime", response.getGenerationTime());

            log.info("문제 전달 완료 - problemId: {}", response.getProblemId());

            // 비동기로 풀 보충
            refillPoolAsync(difficulty, topic, theme);

            return ResponseEntity.ok(ApiResponse.success(responseData));

        } catch (Exception e) {
            log.error("문제 꺼내기 실패", e);
            throw new CustomBusinessException(AlgoErrorCode.PROBLEM_GENERATION_FAIL);
        }
    }

    /**
     * 풀 상태 조회
     *
     * GET /api/algo/pool/status
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<PoolStatusDto>> getPoolStatus() {
        log.info("풀 상태 조회 요청");

        try {
            PoolStatusDto status = poolService.getPoolStatus(ACTIVE_THEMES);

            log.info("풀 상태 - 총: {}/{}, 채우기율: {}%",
                    status.getTotalCount(), status.getTargetTotal(), status.getFillRate());

            return ResponseEntity.ok(ApiResponse.success(status));

        } catch (Exception e) {
            log.error("풀 상태 조회 실패", e);
            throw new CustomBusinessException(AlgoErrorCode.INVALID_INPUT);
        }
    }

    /**
     * 풀 수동 채우기 (관리자용)
     *
     * POST /api/algo/pool/refill?difficulty=GOLD&topic=DFS/BFS&theme=SANTA_DELIVERY
     */
    @PostMapping("/refill")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refillPool(
            @RequestParam String difficulty,
            @RequestParam String topic,
            @RequestParam String theme) {

        log.info("풀 수동 채우기 요청 - difficulty: {}, topic: {}, theme: {}", difficulty, topic, theme);

        try {
            Long poolId = poolService.generateForPool(difficulty, topic, theme);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("poolId", poolId);
            responseData.put("message", "풀에 문제가 추가되었습니다.");

            log.info("풀 수동 채우기 완료 - poolId: {}", poolId);

            return ResponseEntity.ok(ApiResponse.success(responseData));

        } catch (Exception e) {
            log.error("풀 수동 채우기 실패", e);
            throw new CustomBusinessException(AlgoErrorCode.PROBLEM_GENERATION_FAIL);
        }
    }

    /**
     * 특정 조합의 풀 개수 조회
     *
     * GET /api/algo/pool/count?difficulty=GOLD&topic=DFS/BFS&theme=SANTA_DELIVERY
     */
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPoolCount(
            @RequestParam String difficulty,
            @RequestParam String topic,
            @RequestParam String theme) {

        log.info("풀 개수 조회 - difficulty: {}, topic: {}, theme: {}", difficulty, topic, theme);

        try {
            int count = poolService.getCountByCombination(difficulty, topic, theme);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("difficulty", difficulty);
            responseData.put("topic", topic);
            responseData.put("theme", theme);
            responseData.put("count", count);

            return ResponseEntity.ok(ApiResponse.success(responseData));

        } catch (Exception e) {
            log.error("풀 개수 조회 실패", e);
            throw new CustomBusinessException(AlgoErrorCode.INVALID_INPUT);
        }
    }

    /**
     * 비동기 풀 보충
     * <p>사용자 요청 처리 후 백그라운드에서 풀 보충
     */
    @Async
    protected void refillPoolAsync(String difficulty, String topic, String theme) {
        try {
            log.info("비동기 풀 보충 시작 - difficulty: {}, topic: {}, theme: {}", difficulty, topic, theme);
            poolService.generateForPool(difficulty, topic, theme);
            log.info("비동기 풀 보충 완료 - difficulty: {}, topic: {}, theme: {}", difficulty, topic, theme);
        } catch (Exception e) {
            log.error("비동기 풀 보충 실패 - difficulty: {}, topic: {}, theme: {}", difficulty, topic, theme, e);
        }
    }
}
