package kr.or.kosa.backend.tutor.controller;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.concurrent.Executor;

import kr.or.kosa.backend.tutor.dto.TutorClientMessage;
import kr.or.kosa.backend.tutor.dto.TutorServerMessage;
import kr.or.kosa.backend.tutor.service.TutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class TutorWebSocketController {

    private static final String USER_QUEUE_DEST = "/queue/tutor";
    private static final int CODE_MAX_BYTES = 100 * 1024;
    private static final int MESSAGE_MAX_CHARS = 1_000;
    private static final int LANGUAGE_MAX_CHARS = 50;

    private final SimpMessagingTemplate messagingTemplate;
    private final TutorService tutorService;

    @Qualifier("tutorWsExecutor")
    private final Executor tutorWsExecutor;

    @MessageMapping("/tutor.ask")
    public void handleTutorMessage(@Payload TutorClientMessage clientMessage, Principal principal) {

        if (clientMessage == null) {
            log.warn("[Tutor] Received null TutorClientMessage payload");
            return;
        }

        String userId = resolveUserId(principal);
        if (userId == null) {
            log.warn("[Tutor] Dropping message without authenticated user");
            return;
        }

        // principalName / resolvedUserId 확인용(중요)
        String principalName = (principal != null ? principal.getName() : null);
        log.info("[Tutor] principalName={} resolvedUserId={}", principalName, userId);

        // 서버 기준 userId로 덮어쓰기
        clientMessage.setUserId(userId);

        if (!validatePayload(clientMessage, userId)) {
            return;
        }

        // ===== 요약 로그(IN) =====
        int codeBytes = safeUtf8Bytes(clientMessage.getCode());
        int codeLines = safeLineCount(clientMessage.getCode());
        int msgLen = clientMessage.getMessage() == null ? 0 : clientMessage.getMessage().length();
        String codeHash = clientMessage.getCode() == null ? "0" : Integer.toHexString(clientMessage.getCode().hashCode());

        log.info("[Tutor] IN userId={} problemId={} trigger={} lang={} codeBytes={} codeLines={} msgLen={} codeHash={}",
                userId,
                clientMessage.getProblemId(),
                clientMessage.getTriggerType(),
                clientMessage.getLanguage(),
                codeBytes,
                codeLines,
                msgLen,
                codeHash
        );

        // 즉시 ACK(처리 중 안내) 전송
        sendInfo(userId, clientMessage, "튜터가 요청을 처리 중입니다...");

        SecurityContext context = buildSecurityContext(principal);

        tutorWsExecutor.execute(() -> {
            SecurityContextHolder.setContext(context);
            long start = System.currentTimeMillis();

            try {
                TutorServerMessage response = tutorService.handleMessage(clientMessage);
                long elapsedMs = System.currentTimeMillis() - start;

                // ===== 요약 로그(OUT) =====
                if (response == null) {
                    log.warn("[Tutor] OUT service={} elapsedMs={} response=null userId={} problemId={}",
                            tutorService.getClass().getSimpleName(),
                            elapsedMs,
                            userId,
                            clientMessage.getProblemId()
                    );
                    return;
                }

                if (response.getUserId() == null) {
                    response.setUserId(userId);
                }

                int contentLen = response.getContent() == null ? 0 : response.getContent().length();

                log.info("[Tutor] OUT service={} elapsedMs={} type={} contentLen={} userId={} problemId={}",
                        tutorService.getClass().getSimpleName(),
                        elapsedMs,
                        response.getType(),
                        contentLen,
                        userId,
                        clientMessage.getProblemId()
                );

                messagingTemplate.convertAndSendToUser(userId, USER_QUEUE_DEST, response);

            } catch (Exception e) {
                long elapsedMs = System.currentTimeMillis() - start;
                log.error("[Tutor] handleMessage async error elapsedMs={} userId={} problemId={}",
                        elapsedMs, userId, clientMessage.getProblemId(), e);
                sendError(userId, clientMessage, "튜터 응답 처리 중 오류가 발생했습니다.");
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
    }

    private boolean validatePayload(TutorClientMessage clientMessage, String userId) {
        if (clientMessage.getCode() != null &&
                clientMessage.getCode().getBytes(StandardCharsets.UTF_8).length > CODE_MAX_BYTES) {

            int len = clientMessage.getCode().getBytes(StandardCharsets.UTF_8).length;
            log.warn("[Tutor] code too large problemId={} userId={} length={}bytes",
                    clientMessage.getProblemId(), userId, len);

            sendError(userId, clientMessage, "코드 길이가 제한(100KB)을 초과했습니다.");
            return false;
        }

        if (StringUtils.length(clientMessage.getMessage()) > MESSAGE_MAX_CHARS) {
            log.warn("[Tutor] message too long problemId={} userId={} length={}",
                    clientMessage.getProblemId(), userId, StringUtils.length(clientMessage.getMessage()));

            sendError(userId, clientMessage, "질문 길이가 제한(1000자)을 초과했습니다.");
            return false;
        }

        if (StringUtils.length(clientMessage.getLanguage()) > LANGUAGE_MAX_CHARS) {
            log.warn("[Tutor] language too long problemId={} userId={} length={}",
                    clientMessage.getProblemId(), userId, StringUtils.length(clientMessage.getLanguage()));

            sendError(userId, clientMessage, "언어 필드 길이가 너무 깁니다.");
            return false;
        }

        return true;
    }

    private void sendError(String userId, TutorClientMessage clientMessage, String message) {
        TutorServerMessage error = TutorServerMessage.builder()
                .type("ERROR")
                .triggerType(clientMessage.getTriggerType())
                .problemId(clientMessage.getProblemId())
                .userId(userId)
                .content(message)
                .build();
        messagingTemplate.convertAndSendToUser(userId, USER_QUEUE_DEST, error);
    }

    private void sendInfo(String userId, TutorClientMessage clientMessage, String message) {
        TutorServerMessage info = TutorServerMessage.builder()
                .type("INFO")
                .triggerType(clientMessage.getTriggerType())
                .problemId(clientMessage.getProblemId())
                .userId(userId)
                .content(message)
                .build();
        messagingTemplate.convertAndSendToUser(userId, USER_QUEUE_DEST, info);
    }

    private String resolveUserId(Principal principal) {
        if (principal instanceof Authentication authentication) {
            if (StringUtils.isNotBlank(authentication.getName())) {
                return authentication.getName();
            }
            if (authentication.getPrincipal() instanceof kr.or.kosa.backend.security.jwt.JwtUserDetails details) {
                return String.valueOf(details.id());
            }
        }
        return null;
    }

    private SecurityContext buildSecurityContext(Principal principal) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        if (principal instanceof Authentication authentication) {
            context.setAuthentication(authentication);
        }
        return context;
    }

    private int safeUtf8Bytes(String s) {
        return (s == null) ? 0 : s.getBytes(StandardCharsets.UTF_8).length;
    }

    private int safeLineCount(String s) {
        if (s == null || s.isBlank()) return 0;
        return (int) s.lines().count();
    }
}
