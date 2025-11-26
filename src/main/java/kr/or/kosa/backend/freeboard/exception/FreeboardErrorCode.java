package kr.or.kosa.backend.freeboard.exception;

import kr.or.kosa.backend.commons.exception.custom.ErrorCode;

public enum FreeboardErrorCode implements ErrorCode {

    // 조회 실패
    FREEBOARD_NOT_FOUND("404_FB_001", "게시글을 찾을 수 없습니다."),

    // 생성/수정 검증
    TITLE_EMPTY("400_FB_002", "제목은 필수입니다."),
    TITLE_TOO_LONG("400_FB_003", "제목은 200자를 초과할 수 없습니다."),
    CONTENT_EMPTY("400_FB_004", "내용은 비어 있을 수 없습니다."),
    BLOCKS_EMPTY("400_FB_005", "블록 데이터가 비어 있습니다."),
    INVALID_BLOCK_FORMAT("400_FB_006", "블록 데이터 형식이 올바르지 않습니다."),
    REPRESENT_IMAGE_TOO_LONG("400_FB_007", "대표 이미지 URL은 500자를 초과할 수 없습니다."),
    DELETED_POST_ACCESS("410_FB_014", "삭제된 게시글입니다."),

    // 권한
    NO_EDIT_PERMISSION("403_FB_008", "게시글 수정 권한이 없습니다."),
    NO_DELETE_PERMISSION("403_FB_009", "게시글 삭제 권한이 없습니다."),

    // DB 실패
    CREATE_FAILED("500_FB_010", "게시글 저장에 실패했습니다."),
    UPDATE_FAILED("500_FB_011", "게시글 수정에 실패했습니다."),
    DELETE_FAILED("500_FB_012", "게시글 삭제에 실패했습니다."),

    // JSON 변환
    JSON_PARSE_ERROR("500_FB_013", "게시글 콘텐츠 변환 중 오류가 발생했습니다.");



    private final String code;
    private final String message;

    FreeboardErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
