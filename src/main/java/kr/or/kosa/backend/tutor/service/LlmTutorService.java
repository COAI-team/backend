package kr.or.kosa.backend.tutor.service;

import kr.or.kosa.backend.algorithm.service.LLMChatService;
import kr.or.kosa.backend.tutor.dto.TutorClientMessage;
import kr.or.kosa.backend.tutor.dto.TutorServerMessage;
import kr.or.kosa.backend.tutor.subscription.SubscriptionTier;
import kr.or.kosa.backend.tutor.subscription.SubscriptionTierResolver;
import kr.or.kosa.backend.tutor.util.TutorCodeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static kr.or.kosa.backend.tutor.prompt.TutorSystemPrompt.LIVE_TUTOR_SYSTEM_PROMPT;

@Slf4j
@Service
@Profile({"default", "prod"})
@RequiredArgsConstructor
public class LlmTutorService implements TutorService {

    private static final long AUTO_MIN_INTERVAL_MS = 8_000L;
    private static final long USER_MIN_INTERVAL_MS = 5_000L;

    private static final int CODE_MAX_BYTES = 100 * 1024;
    private static final int QUESTION_MAX_CHARS = 1_000;

    private static final int LONG_CODE_THRESHOLD_LINES = 500;
    private static final int LONG_CODE_HEAD_KEEP = 350;
    private static final int LONG_CODE_TAIL_KEEP = 150;

    private static final Duration CACHE_TTL = Duration.ofMinutes(15);
    private static final int MAX_LLM_CALLS_PER_MINUTE = 60;
    private static final int MAX_CONCURRENT_PER_USER = 3;

    private final LLMChatService llmChatService;
    private final SubscriptionTierResolver subscriptionTierResolver;

    private final Map<String, Long> lastAutoCallMillis = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUserCallMillis = new ConcurrentHashMap<>();
    private final Map<String, Integer> lastAutoCodeHash = new ConcurrentHashMap<>();
    private final Map<String, Integer> lastNormalizedHash = new ConcurrentHashMap<>();

    private final Map<String, CacheEntry> userAnswerCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry> autoHintCache = new ConcurrentHashMap<>();

    private final Map<String, RateWindow> llmRateWindow = new ConcurrentHashMap<>();
    private final Map<String, Semaphore> concurrentGuard = new ConcurrentHashMap<>();

    private final AtomicLong llmCallCount = new AtomicLong(0);
    private final AtomicLong llmErrorCount = new AtomicLong(0);
    private final AtomicLong llmTotalMillis = new AtomicLong(0);
    private final AtomicLong llmMaxMillis = new AtomicLong(0);

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public TutorServerMessage handleMessage(TutorClientMessage clientMessage) {
        // 0) null 요청 방어
        if (clientMessage == null) {
            log.warn("Tutor message is null");
            return error("요청이 올바르지 않습니다.");
        }

        // 1) SecurityContext에서 userId 시도
        String userId = resolveAuthenticatedUserId();

        // 2) SecurityContext 비어 있으면, 메시지 안의 userId로 폴백
        if (userId == null && clientMessage.getUserId() != null) {
            userId = String.valueOf(clientMessage.getUserId());
            log.info("[Tutor] SecurityContext 비어있어 clientMessage.userId={} 사용", userId);
        }

        // 3) 그래도 없으면 진짜 인증 실패
        if (userId == null) {
            log.warn("Unauthenticated tutor request dropped - problemId={}, triggerType={}",
                    clientMessage.getProblemId(), clientMessage.getTriggerType());
            return error("인증 정보가 없습니다.");
        }

        // length validation
        if (!isCodeSizeValid(clientMessage.getCode())) {
            return error("코드 길이가 제한(100KB)을 초과했습니다.");
        }
        if (!isQuestionSizeValid(clientMessage.getMessage())) {
            return error("질문 길이가 제한(1000자)을 초과했습니다.");
        }

        String trigger = normalizeTrigger(clientMessage.getTriggerType());
        SubscriptionTier tier = subscriptionTierResolver.resolveTier(userId);

        if (!isTriggerAllowed(trigger, tier)) {
            return error(trigger, clientMessage, userId, "해당 기능은 현재 구독 티어에서 사용할 수 없습니다.");
        }

        if (isAutoRateLimited(trigger, clientMessage, userId)) {
            return error(trigger, clientMessage, userId, "자동 힌트는 최소 8초 간격으로만 사용할 수 있습니다.");
        }
        if (isUserRateLimited(trigger, clientMessage, userId)) {
            return error(trigger, clientMessage, userId, "질문은 5초에 한 번만 보낼 수 있습니다.");
        }

        String normalizedCode = TutorCodeUtils.normalizeCode(clientMessage.getCode());
        boolean meaningfulChange = updateAndCheckMeaningfulChange(clientMessage, normalizedCode, userId);

        // cache check
        if ("USER".equals(trigger)) {
            String cacheKey = buildAnswerCacheKey(userId, clientMessage, normalizedCode);
            CacheEntry cached = userAnswerCache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                return TutorServerMessage.builder()
                        .type("HINT")
                        .triggerType(trigger)
                        .problemId(clientMessage.getProblemId())
                        .userId(userId)
                        .content(cached.value)
                        .build();
            }
        } else if ("AUTO".equals(trigger)) {
            String cacheKey = buildAutoCacheKey(userId, clientMessage, normalizedCode);
            CacheEntry cached = autoHintCache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                return TutorServerMessage.builder()
                        .type("HINT")
                        .triggerType(trigger)
                        .problemId(clientMessage.getProblemId())
                        .userId(userId)
                        .content(cached.value)
                        .build();
            }
        }

        if ("AUTO".equals(trigger) && !meaningfulChange) {
            return TutorServerMessage.builder()
                    .type("INFO")
                    .triggerType(trigger)
                    .problemId(clientMessage.getProblemId())
                    .userId(userId)
                    .content("코드에 의미 있는 변경이 없어 자동 힌트를 건너뜁니다. 코드 수정 후 다시 시도하세요.")
                    .build();
        }

        // line trimming for prompt
        String promptCode = prepareCodeForPrompt(clientMessage.getCode());

        // rate limit global per user
        if (!acquireRateLimit(userId)) {
            return error(trigger, clientMessage, userId, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        }
        Semaphore sem = concurrentGuard.computeIfAbsent(userId, k -> new Semaphore(MAX_CONCURRENT_PER_USER));
        boolean acquired = sem.tryAcquire();
        if (!acquired) {
            return error(trigger, clientMessage, userId, "동시 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
        }

        try {
            String userPrompt = buildUserPrompt(clientMessage, trigger, promptCode, normalizedCode);
            String answer = callLlmWithTimeout(userPrompt);

            if ("USER".equals(trigger)) {
                userAnswerCache.put(buildAnswerCacheKey(userId, clientMessage, normalizedCode),
                        CacheEntry.of(answer, CACHE_TTL));
            } else if ("AUTO".equals(trigger)) {
                autoHintCache.put(buildAutoCacheKey(userId, clientMessage, normalizedCode),
                        CacheEntry.of(answer, CACHE_TTL));
            }

            markAutoCall(trigger, clientMessage, userId);
            markUserCall(trigger, clientMessage, userId);
            if ("AUTO".equals(trigger)) {
                rememberLastAutoHash(clientMessage, userId);
            }

            return TutorServerMessage.builder()
                    .type("HINT")
                    .triggerType(trigger)
                    .problemId(clientMessage.getProblemId())
                    .userId(userId)
                    .content(answer)
                    .build();
        } catch (TimeoutException te) {
            llmErrorCount.incrementAndGet();
            log.warn("LLM timeout - userId={}, problemId={}", userId, clientMessage.getProblemId());
            return error(trigger, clientMessage, userId, "튜터 응답이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.");
        } catch (Exception e) {
            llmErrorCount.incrementAndGet();
            log.error("Tutor LLM 호출 실패 - userId={}, problemId={}, trigger={}", userId, clientMessage.getProblemId(), trigger, e);
            return error(trigger, clientMessage, userId, "튜터 응답 생성 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        } finally {
            sem.release();
        }
    }

    private String resolveAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        log.info("[TutorAuth] authentication={}", auth); // 디버깅용 로그

        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof String) {
            return (String) principal;
        }
        if (auth.getName() != null) {
            return auth.getName();
        }
        return null;
    }

    private boolean isCodeSizeValid(String code) {
        if (code == null) {
            return true;
        }
        return code.getBytes(StandardCharsets.UTF_8).length <= CODE_MAX_BYTES;
    }

    private boolean isQuestionSizeValid(String question) {
        if (question == null) {
            return true;
        }
        return question.length() <= QUESTION_MAX_CHARS;
    }

    private String normalizeTrigger(String triggerType) {
        if (triggerType == null || triggerType.isBlank()) {
            return "USER";
        }
        return triggerType.trim().toUpperCase();
    }

    private boolean isTriggerAllowed(String trigger, SubscriptionTier tier) {
        if (tier == null || tier == SubscriptionTier.FREE) {
            return false;
        }
        if ("AUTO".equals(trigger)) {
            return tier == SubscriptionTier.PRO;
        }
        return true; // USER는 BASIC 이상 허용
    }

    private boolean isAutoRateLimited(String trigger, TutorClientMessage clientMessage, String userId) {
        if (!"AUTO".equals(trigger)) {
            return false;
        }
        String key = buildRateLimitKey(clientMessage, trigger, userId);
        long now = System.currentTimeMillis();
        Long last = lastAutoCallMillis.get(key);
        return last != null && (now - last) < AUTO_MIN_INTERVAL_MS;
    }

    private void markAutoCall(String trigger, TutorClientMessage clientMessage, String userId) {
        if (!"AUTO".equals(trigger)) {
            return;
        }
        String key = buildRateLimitKey(clientMessage, trigger, userId);
        lastAutoCallMillis.put(key, System.currentTimeMillis());
    }

    private boolean isUserRateLimited(String trigger, TutorClientMessage clientMessage, String userId) {
        if (!"USER".equals(trigger)) {
            return false;
        }
        String key = buildRateLimitKey(clientMessage, trigger, userId);
        long now = System.currentTimeMillis();
        Long last = lastUserCallMillis.get(key);
        return last != null && (now - last) < USER_MIN_INTERVAL_MS;
    }

    private void markUserCall(String trigger, TutorClientMessage clientMessage, String userId) {
        if (!"USER".equals(trigger)) {
            return;
        }
        String key = buildRateLimitKey(clientMessage, trigger, userId);
        lastUserCallMillis.put(key, System.currentTimeMillis());
    }

    private String buildRateLimitKey(TutorClientMessage clientMessage, String trigger, String userId) {
        return (userId != null ? userId : "null") + "|" +
                (clientMessage.getProblemId() != null ? clientMessage.getProblemId() : "null") + "|" +
                trigger;
    }

    private boolean isSameCodeAsLastAuto(TutorClientMessage clientMessage, String userId) {
        String key = buildRateLimitKey(clientMessage, "AUTO", userId);
        Integer prev = lastAutoCodeHash.get(key);
        int current = computeCodeHash(clientMessage.getCode());
        return prev != null && prev == current;
    }

    private void rememberLastAutoHash(TutorClientMessage clientMessage, String userId) {
        String key = buildRateLimitKey(clientMessage, "AUTO", userId);
        lastAutoCodeHash.put(key, computeCodeHash(clientMessage.getCode()));
    }

    private int computeCodeHash(String code) {
        if (code == null) {
            return 0;
        }
        return Objects.hash(code);
    }

    private boolean updateAndCheckMeaningfulChange(TutorClientMessage clientMessage, String normalizedCode, String userId) {
        String key = userId + "|" + (clientMessage.getProblemId() != null ? clientMessage.getProblemId() : "null");
        Integer prev = lastNormalizedHash.get(key);
        int current = Objects.hash(normalizedCode);
        boolean changed = prev == null || prev != current;
        lastNormalizedHash.put(key, current);
        return changed;
    }

    private String prepareCodeForPrompt(String code) {
        if (code == null) {
            return "";
        }
        String[] lines = code.split("\\R", -1);
        if (lines.length > LONG_CODE_THRESHOLD_LINES) {
            return TutorCodeUtils.trimLongCode(code, LONG_CODE_THRESHOLD_LINES, LONG_CODE_HEAD_KEEP, LONG_CODE_TAIL_KEEP)
                    + "\n// 코드는 일부만 잘려 있습니다.";
        }
        return code;
    }

    private String buildAnswerCacheKey(String userId, TutorClientMessage client, String normalizedCode) {
        String question = client.getMessage() == null ? "" : client.getMessage().trim();
        return Integer.toHexString(Objects.hash(userId, client.getProblemId(), normalizedCode, question));
    }

    private String buildAutoCacheKey(String userId, TutorClientMessage client, String normalizedCode) {
        String signature = client.getJudgeResult() + "|" + client.getPassedCount() + "|" + client.getTotalCount();
        return Integer.toHexString(Objects.hash(userId, client.getProblemId(), normalizedCode, signature, "AUTO_HINT"));
    }

    private String buildUserPrompt(TutorClientMessage client, String trigger, String promptCode, String normalizedCode) {
        String base = """
            [문제 번호]
            %d

            [언어]
            %s

            [학생 코드]
            ```%s
            %s
            ```

            """.formatted(
                client.getProblemId(),
                client.getLanguage(),
                client.getLanguage(),
                promptCode == null ? "" : promptCode
        );

        String judgeMeta = "";
        if (client.getJudgeResult() != null) {
            judgeMeta = """
                [채점 결과]
                - overallResult: %s
                - passedCount: %s
                - totalCount: %s

                """.formatted(
                    client.getJudgeResult(),
                    client.getPassedCount() == null ? "null" : client.getPassedCount(),
                    client.getTotalCount() == null ? "null" : client.getTotalCount()
            );
        }

        String header = base + judgeMeta + """
            [코드 정규화 해시]
            %s

            [답변 스타일 공통 규칙]
            - 답변은 반드시 한국어로 한다.
            - 알고리즘/코딩테스트를 막 시작한 초급자(브론즈 난이도 문제 푸는 수준)도 이해할 수 있게,
              짧고 쉬운 문장으로 설명한다.
            - "시간 복잡도", "공간 복잡도", "자료구조" 같은 어려운 용어를 쓰면,
              바로 이어서 일상적인 한국어로 한 번 더 풀어서 설명한다.
            - 학생이 바로 복사·붙여넣기 해서 제출 가능한 정답 코드를 통째로 작성하는 것은 절대 금지다.
            - 정답에 매우 가까운 전체 코드를 한 번에 보여주지 말고,
              꼭 필요한 경우에만 핵심 한두 줄 정도만 코드 예시로 보여준다.
            """.formatted(Integer.toHexString(Objects.hash(normalizedCode)));

        if ("AUTO".equals(trigger)) {
            // ===== AUTO HINT 모드 =====
            return header + """
                [모드]
                AUTO_HINT

                [지시사항]
                - 학생이 질문을 하지 않았지만, 현재 코드 상태를 보고
                  "지금 다음으로 무엇을 고치면 좋을지"를 알려 주는 짧은 힌트를 제공한다.
                - 최대 2~3개의 bullet로만 답변하고, 각 bullet은 한두 문장 이내로 유지한다.
                - "요구사항을 다시 읽어보세요" 같은 추상적인 말은 피하고,
                  "입력을 여러 줄 읽어야 하는데 한 줄만 읽고 있습니다"처럼
                  구체적으로 무엇을 확인해야 하는지 말해준다.
                - 전체 알고리즘 설계나 완성된 풀이를 설명하지 말고,
                  다음에 확인하거나 수정해야 할 포인트(조건, 루프, 입출력 등)를 중심으로 말한다.
                - 정답 코드를 통째로 제시하거나, 그대로 제출해도 되는 수준의 코드를 주지 않는다.
                """;
        } else {
            // ===== USER QUESTION 모드 =====
            return header + """
                [모드]
                USER_QUESTION

                [학생 질문]
                %s

                [지시사항]
                - 학생에게 직접 말하듯이, 너무 딱딱하지 않은 친절한 말투로 답한다.
                  (예: "~해 볼 수 있어요", "~일 가능성이 커요" 같이 부드러운 표현 사용)
                - 아래 순서를 가능한 한 지키되, 불필요하게 길게 설명하지 말고 핵심 위주로 적는다.

                  1) 현재 코드에서 잘못된 부분이나 빠진 부분을 1~2개 골라,
                     "어디에서 무엇이 문제인지"를 구체적으로 짚어준다.
                     예: "반복문이 0부터 시작해서 첫 번째 원소를 건너뛰고 있어요" 처럼 설명한다.

                  2) 이 문제를 풀기 위한 생각 순서를
                     "1단계, 2단계, 3단계..."처럼 간단한 단계들로 정리해서 요약한다.
                     알고리즘 이름 자체보다, 입력을 어떻게 읽고 어떤 조건을 검사해서
                     어떤 값을 출력해야 하는지 흐름 위주로 설명한다.

                  3) 작은 예시 입력을 하나 상상해서,
                     그 입력이 들어왔을 때 코드가 어떻게 동작해야 하는지
                     말로 따라가며 설명한다. (디버깅하듯이 설명)

                - "시간/공간 복잡도" 같은 이론적인 이야기는 정말 필요한 경우에만
                  한 줄 정도로 짧게 언급하고, 가능하면 생략한다.
                - 학생이 "너는 다른 답변은 못해?" 같은 메타 질문을 한 경우에는,
                  이전 설명이 너무 추상적이었을 수 있음을 인정하고
                  더 쉬운 표현과 다른 예시를 사용해 다시 설명해 준다.
                - 전체 정답 코드를 절대 작성하지 말고,
                  꼭 필요한 경우에만 작은 코드 조각(한두 줄 수준)이나
                  의사코드 형태로 예시를 든다.
                """.formatted(client.getMessage() == null ? "" : client.getMessage());
        }
    }


    private String callLlmWithTimeout(String userPrompt) throws Exception {
        Callable<String> task = () -> llmChatService.callPlain(LIVE_TUTOR_SYSTEM_PROMPT, userPrompt);
        Future<String> future = executor.submit(task);
        long start = System.currentTimeMillis();
        try {
            String result = future.get(10, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;
            recordLlmMetrics(elapsed, false);
            return result;
        } catch (Exception ex) {
            llmErrorCount.incrementAndGet();
            throw ex;
        }
    }

    private void recordLlmMetrics(long elapsedMs, boolean error) {
        llmCallCount.incrementAndGet();
        llmTotalMillis.addAndGet(elapsedMs);
        llmMaxMillis.updateAndGet(prev -> Math.max(prev, elapsedMs));
        if (error) {
            llmErrorCount.incrementAndGet();
        }
    }

    private boolean acquireRateLimit(String userId) {
        long now = System.currentTimeMillis();
        RateWindow window = llmRateWindow.computeIfAbsent(userId, k -> new RateWindow(now, new AtomicInteger(0)));
        synchronized (window) {
            if (now - window.windowStart >= 60_000L) {
                window.windowStart = now;
                window.counter.set(0);
            }
            if (window.counter.incrementAndGet() > MAX_LLM_CALLS_PER_MINUTE) {
                return false;
            }
        }
        return true;
    }

    private TutorServerMessage error(String message) {
        return TutorServerMessage.builder()
                .type("ERROR")
                .content(message)
                .build();
    }

    private TutorServerMessage error(String trigger, TutorClientMessage clientMessage, String userId, String message) {
        return TutorServerMessage.builder()
                .type("ERROR")
                .triggerType(trigger)
                .problemId(clientMessage.getProblemId())
                .userId(userId)
                .content(message)
                .build();
    }

    private static class CacheEntry {
        private final String value;
        private final long expiresAt;

        private CacheEntry(String value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        static CacheEntry of(String value, Duration ttl) {
            return new CacheEntry(value, System.currentTimeMillis() + ttl.toMillis());
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private static class RateWindow {
        private long windowStart;
        private final AtomicInteger counter;

        RateWindow(long windowStart, AtomicInteger counter) {
            this.windowStart = windowStart;
            this.counter = counter;
        }
    }
}
