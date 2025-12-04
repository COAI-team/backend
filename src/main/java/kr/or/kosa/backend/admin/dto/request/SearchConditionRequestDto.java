package kr.or.kosa.backend.admin.dto.request;

import kr.or.kosa.backend.admin.exception.AdminErrorCode;
import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;

public record SearchConditionRequestDto(int page, int size, String userEmail, Object filter, String sortField, String sortOrder) {
    public SearchConditionRequestDto() {
        this(1, 10, "", "", "","");
    }


    public SearchConditionRequestDto(int page, int size, String userEmail, Object filter, String sortField, String sortOrder) {
        this.page = validatePage(page);
        this.size = validateSize(size);
        this.userEmail = userEmail;
        this.filter = validateUserFilter(filter);
        this.sortField = sortField;
        this.sortOrder = sortOrder;
    }

    private Object validateUserFilter(Object filter){
        if (filter == null) return "";
        Object result = null;

        if ("all".equals(filter)) result = "";
        else if ("admin".equals(filter)) result = "ROLE_ADMIN";
        else if ("user".equals(filter)) result = "ROLE_USER";
        else if ("active".equals(filter)) result = 0;
        else if ("deleted".equals(filter)) result = 1;

        if (result == null) throw new CustomBusinessException(AdminErrorCode.ADMIN_ROLE);
        return result;
        //가입중
//        else if(userRole.equals("active")) result = "가입중일때?";
        // 탈퇴
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
