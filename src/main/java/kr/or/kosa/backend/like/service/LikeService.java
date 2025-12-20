package kr.or.kosa.backend.like.service;

import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import kr.or.kosa.backend.like.domain.Like;
import kr.or.kosa.backend.like.domain.ReferenceType;
import kr.or.kosa.backend.like.dto.LikeToggleResponse;
import kr.or.kosa.backend.like.dto.LikeUserDto;
import kr.or.kosa.backend.like.exception.LikeErrorCode;
import kr.or.kosa.backend.like.mapper.LikeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LikeService {

    private final LikeMapper likeMapper;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String LIKE_RATE_LIMIT_KEY = "like:rate_limit:";
    private static final long RATE_LIMIT_SECONDS = 3; // 3초에 1번만 허용

    @Transactional
    public LikeToggleResponse toggleLike(Long userId, ReferenceType referenceType, Long referenceId) {
        if (userId == null) {
            throw new CustomBusinessException(LikeErrorCode.UNAUTHORIZED);
        }

        if (referenceId == null || referenceType == null) {
            throw new CustomBusinessException(LikeErrorCode.INVALID_REFERENCE);
        }

        // Rate Limiting 체크
        String rateLimitKey = LIKE_RATE_LIMIT_KEY + userId + ":" + referenceType + ":" + referenceId;

        Boolean isAllowed;
        try {
            isAllowed = redisTemplate.opsForValue().setIfAbsent(
                    rateLimitKey,
                    "1",
                    RATE_LIMIT_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            log.error("Redis Rate Limit 체크 실패 - fallback 허용", e);
            isAllowed = true;
        }

        if (Boolean.FALSE.equals(isAllowed)) {
            log.warn("좋아요 연타 감지 - userId: {}, referenceType: {}, referenceId: {}",
                    userId, referenceType, referenceId);
            throw new CustomBusinessException(LikeErrorCode.TOO_MANY_REQUESTS);
        }

        Like existingLike = likeMapper.selectLike(userId, referenceType, referenceId);

        boolean liked;
        if (existingLike != null) {
            // 좋아요 취소
            int deleted = likeMapper.deleteLike(userId, referenceType, referenceId);
            if (deleted == 0) {
                throw new CustomBusinessException(LikeErrorCode.DELETE_ERROR);
            }
            log.info("좋아요 취소 완료 - userId: {}, referenceType: {}, referenceId: {}",
                    userId, referenceType, referenceId);
            liked = false;
        } else {
            // 좋아요 추가
            Like likeRecord = Like.builder()
                    .userId(userId)
                    .referenceType(referenceType)
                    .referenceId(referenceId)
                    .build();

            int inserted = likeMapper.insertLike(likeRecord);
            if (inserted == 0) {
                throw new CustomBusinessException(LikeErrorCode.INSERT_ERROR);
            }
            log.info("좋아요 추가 완료 - userId: {}, referenceType: {}, referenceId: {}",
                    userId, referenceType, referenceId);
            liked = true;
        }

        // 현재 좋아요 수 조회
        int likeCount = likeMapper.countLikes(referenceType, referenceId);

        return LikeToggleResponse.builder()
                .liked(liked)
                .likeCount(likeCount)
                .build();
    }

    public List<Long> getLikedIds(Long userId, ReferenceType referenceType, List<Long> referenceIds) {
        if (userId == null) {
            throw new CustomBusinessException(LikeErrorCode.UNAUTHORIZED);
        }

        if (referenceIds == null || referenceIds.isEmpty()) {
            return Collections.emptyList();
        }

        return likeMapper.selectLikedReferenceIds(userId, referenceType, referenceIds);
    }

    public List<LikeUserDto> getLikeUsers(ReferenceType referenceType, Long referenceId, int limit) {
        if (referenceId == null || referenceType == null) {
            throw new CustomBusinessException(LikeErrorCode.INVALID_REFERENCE);
        }

        return likeMapper.selectLikeUsers(referenceType, referenceId, limit);
    }

    @Transactional
    public void deleteByReference(ReferenceType referenceType, Long referenceId) {
        if (referenceId == null || referenceType == null) {
            throw new CustomBusinessException(LikeErrorCode.INVALID_REFERENCE);
        }

        int deleted = likeMapper.deleteByReference(referenceType, referenceId);
        log.info("참조 데이터 삭제에 따른 좋아요 삭제 완료 - deletedCount={}, referenceType={}, referenceId={}",
                deleted, referenceType, referenceId);
    }
}