package kr.or.kosa.backend.auth.github.exception;

import kr.or.kosa.backend.commons.exception.custom.ErrorCode;

public enum GithubErrorCode implements ErrorCode {

    TOKEN_RESPONSE_NULL("GIT007", "GitHub Access Token 응답이 비었습니다."),
    TOKEN_MISSING("GIT008", "GitHub 액세스 토큰을 가져올 수 없습니다.");

    private final String code;
    private final String message;

    GithubErrorCode(String code, String message) {
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