package kr.or.kosa.backend.chatbot.controller;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import kr.or.kosa.backend.chatbot.dto.ChatRequestDto;
import kr.or.kosa.backend.chatbot.dto.ChatResponseDto;
import kr.or.kosa.backend.chatbot.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Slf4j
@Validated  // ✅ Validation 활성화
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    @PostMapping("/messages")
    public ChatResponseDto sendMessage(@RequestBody ChatRequestDto request) {
        log.info("Chat request - userId: {}, content: {}", request.getUserId(), request.getContent());
        return chatMessageService.sendMessage(request);
    }

    @GetMapping("/messages")
    public ResponseEntity<ChatResponseDto> getMessages(
            @RequestParam(name = "sessionId", defaultValue = "1") @Min(1) Long sessionId,
            @RequestParam(name = "limit", defaultValue = "50") @Min(1) @Max(100) int limit,
            Authentication authentication  // ✅ JWT에서 사용자 추출
    ) {
        // 인증 체크
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Unauthorized access to /chat/messages");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            Long userId = Long.valueOf(authentication.getName());
            log.info("Get messages - userId: {}, sessionId: {}", userId, sessionId);

            ChatResponseDto result = chatMessageService.getMessages(sessionId, limit, userId);
            return ResponseEntity.ok(result);
        } catch (NumberFormatException e) {
            log.error("Invalid userId from authentication: {}", authentication.getName(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error fetching messages for userId: {}, sessionId: {}",
                    authentication.getName(), sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}