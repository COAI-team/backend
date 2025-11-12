package kr.or.kosa.backend.freeboardLike.domain;

import lombok.Data;

@Data
public class FreeboardLike {
    private Long freeboardId; // 게시글 ID (FK)
    private Long userId;      // 좋아요 누른 유저 ID (FK)
}