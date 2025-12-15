package kr.or.kosa.backend.like.controller;

import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import kr.or.kosa.backend.commons.response.ApiResponse;
import kr.or.kosa.backend.like.domain.ReferenceType;
import kr.or.kosa.backend.like.dto.LikeUserDto;
import kr.or.kosa.backend.like.exception.LikeErrorCode;
import kr.or.kosa.backend.like.service.LikeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/like")
@RequiredArgsConstructor
@Slf4j
public class LikeController {

    private final LikeService likeService;

    @PostMapping("/{referenceType}/{referenceId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleLike(
            @PathVariable String referenceType,
            @PathVariable Long referenceId,
            @RequestAttribute("userId") Long userId
    ) {

        log.info("userId from RequestAttribute = {}", userId);

        ReferenceType type;
        try {
            if ("comment".equalsIgnoreCase(referenceType)) {
                type = ReferenceType.COMMENT;
            } else {
                type = ReferenceType.valueOf("POST_" + referenceType.toUpperCase());
            }
        } catch (IllegalArgumentException e) {
            throw new CustomBusinessException(LikeErrorCode.INVALID_REFERENCE);
        }

        boolean isLiked = likeService.toggleLike(userId, type, referenceId);

        Map<String, Object> response = new HashMap<>();
        response.put("isLiked", isLiked);

        return ResponseEntity.ok(ApiResponse.success(response));
    }


    @GetMapping("/{referenceType}/{referenceId}/users")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLikeUsers(
            @PathVariable String referenceType,
            @PathVariable Long referenceId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        ReferenceType type = ReferenceType.valueOf("POST_" + referenceType.toUpperCase());
        List<LikeUserDto> likeUsers = likeService.getLikeUsers(type, referenceId, limit);

        Map<String, Object> response = new HashMap<>();
        response.put("users", likeUsers);
        response.put("total", likeUsers.size());

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}