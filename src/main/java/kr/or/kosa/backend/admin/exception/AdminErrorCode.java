package kr.or.kosa.backend.admin.exception;

import kr.or.kosa.backend.commons.exception.custom.ErrorCode;

public enum AdminErrorCode implements ErrorCode {
    ADMIN_PAGE("AD001", "입력받은 페이지가 0보다 작습니다."),
    ADMIN_SIZE("AD002", "입력받은 사이즈가 10,20,30이 아닙니다."),
    ADMIN_ROLE("AD003", "입력받은 권한은 없는 권한입니다."),
    ADMIN_BOARD_TYPE("AD004", "입력받은 게시판 종류의 값이 잘못되었습니다."),
    ADMIN_SEARCH_TYPE("AD005", "입력받은 검색 조건이 잘못되었습니다."),
    ADMIN_SORT_FIELD("AD006", "입력받은 정렬 필드 조건이 잘못되었습니다."),
    ADMIN_SORT_ORDER("AD007", "입력받은 정렬 조건이 잘못되었습니다."),
    ADMIN_BOARD_UPDATE("A008", "업데이트에 실패했습니다.")
    ;


    private final String code;
    private final String message;
    AdminErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return this.code;
    }

    @Override
    public String getMessage() {
        return this.message;
    }
}
