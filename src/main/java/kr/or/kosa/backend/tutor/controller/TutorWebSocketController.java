package kr.or.kosa.backend.tutor.controller;

import kr.or.kosa.backend.tutor.dto.TutorClientMessage;
import kr.or.kosa.backend.tutor.dto.TutorServerMessage;
import kr.or.kosa.backend.tutor.service.TutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class TutorWebSocketController {

    private static final String TOPIC_PREFIX = "/topic/tutor";
    private static final int CODE_MAX_BYTES = 100 * 1024;
    private static final int MESSAGE_MAX_CHARS = 1_000;
    private static final int LANGUAGE_MAX_CHARS = 50;

    private final SimpMessagingTemplate messagingTemplate;
    private final TutorService tutorService;

    @MessageMapping("/tutor.ask")
    public void handleTutorMessage(@Payload TutorClientMessage clientMessage) {
        log.info("[Tutor] received message: {}", clientMessage);
        if (clientMessage == null) {
            log.warn("Received null TutorClientMessage payload");
            return;
        }
        if (!validatePayload(clientMessage)) {
            return;
        }

        Long problemId = clientMessage.getProblemId();
        String destination = problemId != null ? TOPIC_PREFIX + "." + problemId : TOPIC_PREFIX;

        TutorServerMessage response = tutorService.handleMessage(clientMessage);

        log.info("[Tutor] service={} response={}",
                tutorService.getClass().getSimpleName(), response);

        if (response != null) {
            messagingTemplate.convertAndSend(destination, response);
            log.info("[Tutor] sent to {}: {}", destination, response);
        } else {
            log.warn("[Tutor] response is null, nothing sent");
        }
    }

    private boolean validatePayload(TutorClientMessage clientMessage) {
        if (clientMessage == null) {
            return false;
        }
        if (clientMessage.getCode() != null &&
                clientMessage.getCode().getBytes().length > CODE_MAX_BYTES) {
            log.warn("[Tutor] code too large problemId={} userId={} length={}bytes",
                    clientMessage.getProblemId(), clientMessage.getUserId(),
                    clientMessage.getCode().getBytes().length);
            sendError(clientMessage, "코드 길이가 제한(100KB)을 초과했습니다.");
            return false;
        }
        if (StringUtils.length(clientMessage.getMessage()) > MESSAGE_MAX_CHARS) {
            log.warn("[Tutor] message too long problemId={} userId={} length={}",
                    clientMessage.getProblemId(), clientMessage.getUserId(),
                    StringUtils.length(clientMessage.getMessage()));
            sendError(clientMessage, "질문 길이가 제한(1000자)을 초과했습니다.");
            return false;
        }
        if (StringUtils.length(clientMessage.getLanguage()) > LANGUAGE_MAX_CHARS) {
            log.warn("[Tutor] language too long problemId={} userId={} length={}",
                    clientMessage.getProblemId(), clientMessage.getUserId(),
                    StringUtils.length(clientMessage.getLanguage()));
            sendError(clientMessage, "언어 필드 길이가 너무 깁니다.");
            return false;
        }
        return true;
    }

    private void sendError(TutorClientMessage clientMessage, String message) {
        Long problemId = clientMessage.getProblemId();
        String destination = problemId != null ? TOPIC_PREFIX + "." + problemId : TOPIC_PREFIX;
        TutorServerMessage error = TutorServerMessage.builder()
                .type("ERROR")
                .triggerType(clientMessage.getTriggerType())
                .problemId(clientMessage.getProblemId())
                .userId(clientMessage.getUserId())
                .content(message)
                .build();
        messagingTemplate.convertAndSend(destination, error);
    }
}
