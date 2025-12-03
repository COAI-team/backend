package kr.or.kosa.backend.admin.dto.response;

import java.util.List;

public record PageResponseDto<T>(
    List<T> content,
    int page,
    int size,
    int totalCount,
    int totalPages,
    boolean hasNext,
    boolean hasPrevious
) {
    public PageResponseDto(List<T> content, int page, int size, int totalCount) {
        this(
            content,
            page,
            size,
            totalCount,
            calcTotalPages(totalCount, size),
            hasNext(page, totalCount,size),
            hasPrevious(page)
        );
    }

    private static int calcTotalPages(int totalCount, int size){
        return (int) Math.ceil((double) totalCount / size);
    }

    private static boolean hasNext(int page, int totalCount, int size){
        int totalPages = calcTotalPages(totalCount, size);
        return page < totalPages - 1;
    }

    private static boolean hasPrevious(int page){
        return page > 0;
    }
}
