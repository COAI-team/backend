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

    private static final ResponseEntity<ChatResponseDto> UNAUTHORIZED_RESPONSE =
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    private static final ResponseEntity<ChatResponseDto> BAD_REQUEST_RESPONSE =
            ResponseEntity.status(HttpStatus.BAD_REQUEST).build();

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
            return UNAUTHORIZED_RESPONSE;
        }

        String principal = authentication.getName();
        if (!isNumeric(principal)) {
            log.error("Invalid userId from authentication: {}", principal);
            return BAD_REQUEST_RESPONSE;
        }

        Long userId = Long.parseLong(principal);
        log.info("Get messages - userId: {}, sessionId: {}", userId, sessionId);

        ChatResponseDto result = chatMessageService.getMessages(sessionId, limit, userId);
        return ResponseEntity.ok(result);
    }

    private boolean isNumeric(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch < '0' || ch > '9') {
                return false;
            }
        }
        return true;
    }
}
