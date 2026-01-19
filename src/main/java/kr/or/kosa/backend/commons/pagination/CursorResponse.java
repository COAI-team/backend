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
    private final Long totalElements;

    public CursorResponse(List<T> content, int requestSize, Long totalElements) {
        this.content = content;
        this.size = content.size();
        this.totalElements = totalElements;
        this.hasNext = content.size() >= requestSize;
        this.nextCursor = hasNext && !content.isEmpty()
                ? content.get(content.size() - 1).getId()
                : null;
    }

    // 기존 생성자 유지 (하위 호환성)
    public CursorResponse(List<T> content, int requestSize) {
        this(content, requestSize, null);
    }

}
