package kr.or.kosa.backend.freeboardLike.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import kr.or.kosa.backend.freeboardLike.service.FreeboardLikeService;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequiredArgsConstructor
@RequestMapping("/freeboardlike")
public class FreeboardLikeController {

    private final FreeboardLikeService likeService;

    // ✅ 좋아요 수 조회
    @GetMapping("/{boardId}")
    public Map<String, Object> countLikes(@PathVariable Long boardId) {
        int count = likeService.getLikeCount(boardId);
        Map<String, Object> result = new HashMap<>();
        result.put("likeCount", count);
        return result;
    }

    // ✅ 좋아요 토글 (클릭 시 좋아요 / 취소)
    @PostMapping("/toggle")
    public Map<String, Object> toggleLike(@RequestParam Long boardId, @RequestParam Long userId) {
        boolean liked = likeService.toggleLike(boardId, userId);

        Map<String, Object> response = new HashMap<>();
        response.put("liked", liked);
        response.put("likeCount", likeService.getLikeCount(boardId));

        return response;
    }
}
