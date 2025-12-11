package kr.or.kosa.backend.admin.dto.request;

import kr.or.kosa.backend.admin.exception.AdminErrorCode;
import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;

public record SearchConditionRequestDto(int page, int size, String userEmail, Object roleFilter, String sortField, String sortOrder, Object statusFilter) {
    public SearchConditionRequestDto() {
        this(1, 10, "", "", "","","");
    }


    public SearchConditionRequestDto(int page, int size, String userEmail, Object roleFilter, String sortField, String sortOrder ,Object statusFilter) {
        this.page = validatePage(page);
        this.size = validateSize(size);
        this.userEmail = userEmail;
        this.roleFilter = validateUserFilter(roleFilter);
        this.sortField = sortField;
        this.sortOrder = sortOrder;
        this.statusFilter = validateStatusFilter(statusFilter);
    }

    private Object validateUserFilter(Object roleFilter){
        if (roleFilter == null) return "";
        Object result = null;

        if ("all".equals(roleFilter)) result = "";
        else if ("admin".equals(roleFilter)) result = "ROLE_ADMIN";
        else if ("user".equals(roleFilter)) result = "ROLE_USER";

        if (result == null) throw new CustomBusinessException(AdminErrorCode.ADMIN_ROLE);
        return result;
        //가입중
//        else if(userRole.equals("active")) result = "가입중일때?";
        // 탈퇴
    }

    private Object validateStatusFilter(Object statusFilter){
        if (statusFilter == null) return "";
        Object result = null;

        if ("all".equals(statusFilter)) result = "";
        else if ("active".equals(statusFilter)) result = 0;
        else if ("deleted".equals(statusFilter)) result = 1;

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
