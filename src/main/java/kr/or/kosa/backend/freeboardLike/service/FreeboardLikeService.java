package kr.or.kosa.backend.freeboardLike.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import kr.or.kosa.backend.freeboardLike.mapper.FreeboardLikeMapper;

@Service
@RequiredArgsConstructor
public class FreeboardLikeService {

    private final FreeboardLikeMapper mapper;

    // 좋아요 수 조회
    public int getLikeCount(Long freeboardId) {
        return mapper.countByBoardId(freeboardId);
    }

    // 좋아요 토글 (이미 눌렀으면 삭제, 아니면 등록)
    public boolean toggleLike(Long freeboardId, Long userId) {
        boolean exists = mapper.exists(freeboardId, userId).isPresent();

        if (exists) {
            mapper.deleteLike(freeboardId, userId);
            return false; // 좋아요 취소
        } else {
            mapper.insertLike(freeboardId, userId);
            return true; // 좋아요 등록
        }
    }
}
