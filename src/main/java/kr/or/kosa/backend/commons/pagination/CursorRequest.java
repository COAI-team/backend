package kr.or.kosa.backend.commons.pagination;

import lombok.Getter;

// 커서 기반 페이지네이션 (무한 스크롤 전용)
@Getter
public class CursorRequest {
    private final Long cursor; // 마지막으로 본 데이터의 ID
    private final int size;

    public CursorRequest(Long cursor, int size) {
        this.cursor = cursor;
        this.size = Math.max(size, 1);
    }

    // 정적 팩토리 메서드 - 알고리즘 댓글 사용
    public static CursorRequest of(Long cursor, int size) {
        return new CursorRequest(cursor, size);
    }

    public boolean hasCursor() {
        return cursor != null && cursor > 0;
    }
}