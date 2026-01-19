package kr.or.kosa.backend.commons.pagination;

import lombok.Getter;

// 검색 조건 공통 객체

@Getter
public class SearchCondition {
    private final String keyword;

    public SearchCondition(String keyword) {
        this.keyword = keyword;
    }

    public boolean hasKeyword() {
        return keyword != null && !keyword.isBlank();
    }

}
