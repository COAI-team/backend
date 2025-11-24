package kr.or.kosa.backend.codenose.controller;

import kr.or.kosa.backend.codenose.dto.RagDto;
import kr.or.kosa.backend.codenose.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    @PostMapping("/ingest")
    public ResponseEntity<Void> ingest(@RequestBody RagDto.IngestRequest request) {
        ragService.ingestCode(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/feedback")
    public ResponseEntity<RagDto.FeedbackResponse> getFeedback(@RequestBody RagDto.FeedbackRequest request) {
        String answer = ragService.getPersonalizedFeedback(request);
        return ResponseEntity.ok(new RagDto.FeedbackResponse(answer));
    }
}
