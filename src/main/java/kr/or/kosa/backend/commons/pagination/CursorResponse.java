package kr.or.kosa.backend.commons.pagination;

import lombok.Getter;
import java.util.List;

// 댓글 무한 스크롤 응답
@Getter
public class CursorResponse<T extends Identifiable> {

    private final List<T> content;
    private final Long nextCursor; // 다음 요청에 사용할 커서
    private final boolean hasNext;
    private final int size;        // 요청 크기

    public CursorResponse(List<T> content, int requestSize) {
        this.content = content;
        this.size = content.size();
        this.hasNext = content.size() >= requestSize;
        this.nextCursor = hasNext && !content.isEmpty()
                ? content.get(content.size() - 1).getId()
                : null;
    }

}
