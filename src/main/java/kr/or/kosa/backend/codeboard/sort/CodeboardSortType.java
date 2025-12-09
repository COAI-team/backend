package kr.or.kosa.backend.codeboard.sort;

import kr.or.kosa.backend.codeboard.exception.CodeboardErrorCode;
import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import lombok.Getter;

// 코드게시판 정렬 타입
// 컨트롤러에서 전달된 sort 파라미터를 도메인 정책(enum)으로 변환하기 위한 용도

public enum CodeboardSortType {

    CREATED_AT("created_at"),
    VIEW_COUNT("view_count"),
    LIKE_COUNT("like_count"),
    COMMENT_COUNT("comment_count");

    @Getter
    private final String column;

    CodeboardSortType(String column) {
        this.column = column;
    }

    // sort 파라미터 문자열을 CodeboardSortType으로 변환
    // 허용되지 않은 값이 들어오면 CustomBusinessException 발생
    public static CodeboardSortType from(String sort) {
        if (sort == null || sort.isBlank()) {
            return CREATED_AT;
        }

        // Enum 이름과 비교 (대소문자 무시)
        String upperSort = sort.toUpperCase();
        for (CodeboardSortType type : values()) {
            if (type.name().equals(upperSort)) {
                return type;
            }
        }

        throw new CustomBusinessException(CodeboardErrorCode.INVALID_SORT);
    }
}