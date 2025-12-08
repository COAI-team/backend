package kr.or.kosa.backend.commons.pagination;

import lombok.Getter;

import java.util.List;

// 댓글 무한 스크롤 응답
@Getter
public class CursorResponse<T> {
    private final List<T> content;
    private final Long nextCursor; // 다음 요청에 사용할 커서
    private final boolean hasNext;

    public CursorResponse(List<T> content, Long nextCursor, boolean hasNext) {
        this.content = content;
        this.nextCursor = nextCursor;
        this.hasNext = hasNext;
    }
}
