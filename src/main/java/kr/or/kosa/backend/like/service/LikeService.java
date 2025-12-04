package kr.or.kosa.backend.like.service;

import kr.or.kosa.backend.codeboard.mapper.CodeboardMapper;
import kr.or.kosa.backend.freeboard.mapper.FreeboardMapper;
import kr.or.kosa.backend.comment.mapper.CommentMapper;
import kr.or.kosa.backend.like.domain.Like;
import kr.or.kosa.backend.like.domain.ReferenceType;
import kr.or.kosa.backend.like.mapper.LikeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LikeService {

    private final LikeMapper likeMapper;
    private final CodeboardMapper codeboardMapper;
    private final FreeboardMapper freeboardMapper;
    private final CommentMapper commentMapper;

    @Transactional
    public boolean toggleLike(Long userId, ReferenceType referenceType, Long referenceId) {
        Like existingLike = likeMapper.selectLike(userId, referenceType, referenceId);

        if (existingLike != null) {
            // 좋아요 취소
            likeMapper.deleteLike(userId, referenceType, referenceId);
            decrementLikeCount(referenceType, referenceId);
            return false;
        } else {
            // 좋아요 추가
            Like likeRecord = Like.builder()
                    .userId(userId)
                    .referenceType(referenceType)
                    .referenceId(referenceId)
                    .build();
            likeMapper.insertLike(likeRecord);
            incrementLikeCount(referenceType, referenceId);
            return true;
        }
    }

    public List<Long> getLikedIds(Long userId, ReferenceType referenceType, List<Long> referenceIds) {
        if (referenceIds == null || referenceIds.isEmpty()) {
            return Collections.emptyList();
        }

        return likeMapper.selectLikedReferenceIds(userId, referenceType, referenceIds);
    }

    @Transactional
    public void deleteByReference(ReferenceType referenceType, Long referenceId) {
        likeMapper.deleteByReference(referenceType, referenceId);
    }

    private void incrementLikeCount(ReferenceType referenceType, Long referenceId) {
        switch (referenceType) {
            case POST_CODEBOARD -> codeboardMapper.incrementLikeCount(referenceId);
            case POST_FREEBOARD -> freeboardMapper.incrementLikeCount(referenceId);
            case COMMENT -> commentMapper.incrementLikeCount(referenceId);
        }
    }

    private void decrementLikeCount(ReferenceType referenceType, Long referenceId) {
        switch (referenceType) {
            case POST_CODEBOARD -> codeboardMapper.decrementLikeCount(referenceId);
            case POST_FREEBOARD -> freeboardMapper.decrementLikeCount(referenceId);
            case COMMENT -> commentMapper.decrementLikeCount(referenceId);
        }
    }
}
