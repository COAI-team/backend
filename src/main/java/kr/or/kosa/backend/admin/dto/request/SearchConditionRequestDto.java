package kr.or.kosa.backend.admin.dto.request;

import kr.or.kosa.backend.admin.exception.AdminErrorCode;
import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;

public record SearchConditionRequestDto(int page, int size, String userEmail, String userRole) {
    public SearchConditionRequestDto() {
        this(1, 10, "", "");
    }

    public SearchConditionRequestDto(int page, int size, String userEmail, String userRole) {
        this.page = validatePage(page);
        this.size = validateSize(size);
        this.userEmail = userEmail;
        this.userRole = userRole;
    }

    private int validatePage(int page) {
        if (page < 1) {
            throw new CustomBusinessException(AdminErrorCode.ADMIN_PAGE);
        }
        return page;
    }

    private int validateSize(int size) {
        if (size == 10 || size == 20 || size == 30)
            return size;
        throw new CustomBusinessException(AdminErrorCode.ADMIN_SIZE);
    }

    public int getOffset() {
        return (page - 1) * size;
    }
}
