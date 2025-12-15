package kr.or.kosa.backend.algorithm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.algorithm.dto.PoolStatusDto;
import kr.or.kosa.backend.algorithm.dto.response.ProblemGenerationResponseDto;
import kr.or.kosa.backend.algorithm.exception.AlgoErrorCode;
import kr.or.kosa.backend.algorithm.service.ProblemGenerationOrchestrator;
import kr.or.kosa.backend.algorithm.service.ProblemPoolService;
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

        final Long finalUserId = userId;

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

                    // 요청 처리 시간 측정 (fetchTime)
                    double fetchTime = (System.currentTimeMillis() - requestStartTime) / 1000.0;

                    // 풀에서 가져왔는지 판단: 진행률 콜백이 호출되지 않았고, fetchTime이 짧으면 풀에서 가져온 것
                    // progressCallback이 한번도 호출되지 않았으면 fromPoolFlag[0]은 초기값 false 유지
                    // 하지만 실제로 풀에서 가져온 경우에는 progressCallback이 호출되지 않음
                    // fetchTime < 3초이고 generationTime이 있으면 풀에서 가져온 것으로 판단
                    boolean fromPool = fetchTime < 3.0 && response.getGenerationTime() != null;

                    // 완료 이벤트 전송
                    Map<String, Object> completeEvent = new HashMap<>();
                    completeEvent.put("type", "COMPLETE");
                    completeEvent.put("problemId", response.getProblemId());
                    completeEvent.put("title", response.getProblem().getAlgoProblemTitle());
                    completeEvent.put("description", response.getProblem().getAlgoProblemDescription());
                    completeEvent.put("difficulty", response.getProblem().getAlgoProblemDifficulty().name());
                    completeEvent.put("testCaseCount", response.getTestCases() != null ? response.getTestCases().size() : 0);
                    completeEvent.put("generationTime", response.getGenerationTime());  // LLM이 생성하는데 걸린 시간 (풀에 저장된 값)
                    completeEvent.put("fetchTime", fromPool ? fetchTime : null);  // 풀에서 꺼내오는데 걸린 시간
                    completeEvent.put("fromPool", fromPool);

                    sink.next("data: " + objectMapper.writeValueAsString(completeEvent) + "\n\n");
                    sink.complete();

                    log.info("문제 전달 완료 - problemId: {}, fromPool: {}, fetchTime: {}초, generationTime: {}초",
                            response.getProblemId(), fromPool, fetchTime, response.getGenerationTime());

                    // 비동기로 풀 보충 (풀에서 꺼낸 경우)
                    refillPoolAsync(difficulty, topic, theme);

                } catch (Exception e) {
                    log.error("문제 꺼내기 실패", e);
                    try {
                        Map<String, Object> errorEvent = new HashMap<>();
                        errorEvent.put("type", "ERROR");
                        errorEvent.put("message", e.getMessage());
                        sink.next("data: " + objectMapper.writeValueAsString(errorEvent) + "\n\n");
                    } catch (Exception ex) {
                        sink.next("data: {\"type\":\"ERROR\",\"message\":\"" + e.getMessage() + "\"}\n\n");
                    }
                    sink.complete();
                }
            });
        });
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

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("problemId", response.getProblemId());
            responseData.put("title", response.getProblem().getAlgoProblemTitle());
            responseData.put("description", response.getProblem().getAlgoProblemDescription());
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
