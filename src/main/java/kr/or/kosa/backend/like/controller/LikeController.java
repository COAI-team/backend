package kr.or.kosa.backend.like.controller;

import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import kr.or.kosa.backend.commons.response.ApiResponse;
import kr.or.kosa.backend.like.domain.ReferenceType;
import kr.or.kosa.backend.like.dto.LikeToggleResponse;
import kr.or.kosa.backend.like.dto.LikeUserDto;
import kr.or.kosa.backend.like.exception.LikeErrorCode;
import kr.or.kosa.backend.like.service.LikeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/like")
@RequiredArgsConstructor
@Slf4j
public class LikeController {

    private final LikeService likeService;

    @PostMapping("/{referenceType}/{referenceId}")
    public ResponseEntity<ApiResponse<LikeToggleResponse>> toggleLike(
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

        LikeToggleResponse response = likeService.toggleLike(userId, type, referenceId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{referenceType}/{referenceId}/users")
    public ResponseEntity<ApiResponse<List<LikeUserDto>>> getLikeUsers(
            @PathVariable String referenceType,
            @PathVariable Long referenceId,
            @RequestParam(defaultValue = "10") int limit
    ) {
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

        List<LikeUserDto> users = likeService.getLikeUsers(type, referenceId, limit);
        return ResponseEntity.ok(ApiResponse.success(users));
    }
}