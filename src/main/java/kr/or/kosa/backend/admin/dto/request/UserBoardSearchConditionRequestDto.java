package kr.or.kosa.backend.admin.dto.request;

import kr.or.kosa.backend.admin.enums.BoardType;
import kr.or.kosa.backend.admin.exception.AdminErrorCode;
import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;

import java.util.Objects;

public record UserBoardSearchConditionRequestDto(
    int page,
    int size,
    String boardType, // 게시판 종류
    String keyword,
    String searchType, // 검색 조건(제목, 작성자)
    String sortField, // 정렬 컬럼
    String sortOrder  // 오름차순 내림차순
) {
    public UserBoardSearchConditionRequestDto {
        page = validatePage(page);
        size = validateSize(size);
        boardType = boardType == null ? "" : validateBoardType(boardType);
        keyword = keyword == null ? "" : keyword;
        searchType = searchType == null ? "" : validateSearchType(searchType);
        sortField = validateSortField(sortField);
        sortOrder = validateSortOrder(sortOrder);
    }

    private int validatePage(int page) {
        if (page <= 0) throw new CustomBusinessException(AdminErrorCode.ADMIN_PAGE);
        return page;
    }

    private int validateSize(int size) {
        if (size == 10 || size == 20 || size == 30) return size;
        throw new CustomBusinessException(AdminErrorCode.ADMIN_SIZE);
    }

    private String validateBoardType(String boardType) {
        if (boardType == null || boardType.isBlank()) {
            return ""; // 전체 검색 허용
        }
        try {
            BoardType.valueOf(boardType.toLowerCase());
            return boardType.toLowerCase();
        } catch (IllegalArgumentException e) {
            throw new CustomBusinessException(AdminErrorCode.ADMIN_BOARD_TYPE);
        }
    }

    private String validateSearchType(String searchType) {
        if (Objects.equals(searchType, "") ||
            Objects.equals(searchType, "title") ||
            Objects.equals(searchType, "user")
        ) return searchType;
        throw new CustomBusinessException(AdminErrorCode.ADMIN_SEARCH_TYPE);
    }
    private String validateSortField(String sortField) {
        if (Objects.equals(sortField, "createdAt") ||
            Objects.equals(sortField, "title") ||
            Objects.equals(sortField, "") ||
            Objects.equals(sortField, "userNickName")
         ) return sortField;
        throw new CustomBusinessException(AdminErrorCode.ADMIN_SORT_FIELD);
    }

    private String validateSortOrder(String sortOrder) {
        if (Objects.equals(sortOrder, "asc") ||
            Objects.equals(sortOrder, "desc")
        ) return sortOrder;
        throw new CustomBusinessException(AdminErrorCode.ADMIN_SORT_ORDER);
    }

    public int offset() {
        return (page - 1) * size;
    }
}