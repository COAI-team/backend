package kr.or.kosa.backend.toolbar.link.controller;

import kr.or.kosa.backend.toolbar.link.dto.LinkPreviewRequest;
import kr.or.kosa.backend.toolbar.link.dto.LinkPreviewResponse;
import kr.or.kosa.backend.toolbar.link.service.LinkPreviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/link")
@RequiredArgsConstructor
public class LinkPreviewController {

    private final LinkPreviewService linkPreviewService;

    @PostMapping("/preview")
    public ResponseEntity<LinkPreviewResponse> preview(@RequestBody LinkPreviewRequest request) {
        try {
            LinkPreviewResponse response = linkPreviewService.fetchPreview(request.getUrl());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
