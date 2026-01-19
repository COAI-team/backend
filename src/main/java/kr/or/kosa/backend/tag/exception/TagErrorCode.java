package kr.or.kosa.backend.tag.exception;

import kr.or.kosa.backend.commons.exception.custom.ErrorCode;

public enum TagErrorCode implements ErrorCode {

    // 태그 조회
    TAG_NOT_FOUND("TAG_001", "태그를 찾을 수 없습니다."),

    // 태그 생성/저장
    TAG_EMPTY("TAG_002", "태그는 빈 문자열일 수 없습니다."),
    TAG_TOO_SHORT("TAG_003", "태그는 2자 이상이어야 합니다."),
    TAG_TOO_LONG("TAG_004", "태그는 20자를 초과할 수 없습니다."),
    TAG_TOO_MANY("TAG_005", "태그는 최대 10개까지만 가능합니다."),
    TAG_DUPLICATE("TAG_006", "중복된 태그가 있습니다."),
    TAG_INVALID_CHARACTERS("TAG_007", "태그에 사용할 수 없는 특수문자가 포함되어 있습니다."),
    TAG_SAVE_FAILED("TAG_008", "태그 저장에 실패했습니다."),

    // 게시글-태그 관계
    BOARD_TAG_NOT_FOUND("TAG_009", "게시글의 태그를 찾을 수 없습니다."),
    BOARD_TAG_DELETE_FAILED("TAG_010", "태그 삭제에 실패했습니다.");

    private final String code;
    private final String message;

    TagErrorCode(String code, String message) {
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