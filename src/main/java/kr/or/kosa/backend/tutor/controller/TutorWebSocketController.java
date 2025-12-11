package kr.or.kosa.backend.tutor.controller;

import kr.or.kosa.backend.tutor.dto.TutorClientMessage;
import kr.or.kosa.backend.tutor.dto.TutorServerMessage;
import kr.or.kosa.backend.tutor.service.TutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class TutorWebSocketController {

    private static final String TOPIC_PREFIX = "/topic/tutor";

    private final SimpMessagingTemplate messagingTemplate;
    private final TutorService tutorService;

    @MessageMapping("/tutor.send")
    public void handleTutorMessage(@Payload TutorClientMessage clientMessage) {
        if (clientMessage == null) {
            log.warn("Received null TutorClientMessage payload");
            return;
        }

        Long problemId = clientMessage.getProblemId();
        String destination = problemId != null ? TOPIC_PREFIX + "." + problemId : TOPIC_PREFIX;

        TutorServerMessage response = tutorService.handleMessage(clientMessage);
        if (response != null) {
            messagingTemplate.convertAndSend(destination, response);
        }
    }
}
