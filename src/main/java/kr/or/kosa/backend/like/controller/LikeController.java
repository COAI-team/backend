package kr.or.kosa.backend.like.controller;

import kr.or.kosa.backend.like.domain.ReferenceType;
import kr.or.kosa.backend.like.service.LikeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/like")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    @PostMapping("/{referenceType}/{referenceId}")
    public ResponseEntity<Map<String, Object>> toggleLike(
            @PathVariable String referenceType,
            @PathVariable Long referenceId,
            @RequestAttribute(value = "userId", required = false) Long userId
    ) {
        Long actualUserId = (userId != null) ? userId : 1L;

        ReferenceType type = ReferenceType.valueOf("POST_" + referenceType.toUpperCase());
        boolean isLiked = likeService.toggleLike(actualUserId, type, referenceId);

        Map<String, Object> response = new HashMap<>();
        response.put("isLiked", isLiked);

        return ResponseEntity.ok(response);
    }
}