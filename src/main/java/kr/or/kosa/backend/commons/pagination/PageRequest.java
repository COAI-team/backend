package kr.or.kosa.backend.commons.pagination;

import lombok.Getter;

// 페이지네이션 요청 객체
// page, size 기반 offset 계산 전용

@Getter
public class PageRequest {
    private final int page;
    private final int size;
    private final int offset;

    // 페이지와 사이즈가 1보다 작을 경우 기본값으로 1을 설정하여 오류 방지
    public PageRequest(int page, int size) {
        this.page = Math.max(page, 1);
        this.size = Math.max(size, 1);
        this.offset = (this.page - 1) * this.size;
    }
}
