package kr.or.kosa.backend.chatbot.controller;

import kr.or.kosa.backend.chatbot.dto.ChatRequestDto;
import kr.or.kosa.backend.chatbot.dto.ChatResponseDto;
import kr.or.kosa.backend.chatbot.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    @PostMapping("/messages")
    public ChatResponseDto sendMessage(@RequestBody ChatRequestDto request) {
        return chatMessageService.sendMessage(request);
    }

    @GetMapping("/messages")
    public ChatResponseDto getMessages(
            @RequestParam(name = "sessionId", defaultValue = "1") Long sessionId,
            @RequestParam(name = "limit", defaultValue = "50") int limit
    ) {
        return chatMessageService.getMessages(sessionId, limit);
    }
}
