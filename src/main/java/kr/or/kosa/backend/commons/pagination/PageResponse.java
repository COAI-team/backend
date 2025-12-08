package kr.or.kosa.backend.commons.pagination;

import lombok.Getter;
import java.util.List;

// 모든 리스트 API의 공통 응답 래퍼
// 페이지네이션 공통 응답 객체

@Getter
public class PageResponse<T> {
    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalCount;
    private final int totalPages;
    private final boolean hasNext;
    private final boolean hasPrevious;

    public PageResponse(
            List<T> content,
            PageRequest pageRequest,
            long totalCount
    ) {
        this.content = content;
        this.page = pageRequest.getPage();
        this.size = pageRequest.getSize();
        this.totalCount = totalCount;
        this.totalPages = calculateTotalPages(totalCount, size);
        this.hasNext = page < totalPages;
        this.hasPrevious = page > 1;
    }

    private int calculateTotalPages(long totalCount, int size) {
        // size가 음수거나 0인 경우는 PageRequest 단계에서 막혔지만, 방어적 코드로 한 번 더 체크
        if (totalCount == 0 || size <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalCount / size);
    }
}
