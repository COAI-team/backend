package kr.or.kosa.backend.auth.github.exception;

import kr.or.kosa.backend.commons.exception.custom.ErrorCode;

public enum GithubErrorCode implements ErrorCode {

    TOKEN_RESPONSE_NULL("GIT007", "GitHub Access Token 응답이 비었습니다."),
    TOKEN_MISSING("GIT008", "GitHub 액세스 토큰을 가져올 수 없습니다."),

    // GitHub 자동커밋 관련 에러 코드 (2025-12-13)
    GITHUB_NOT_CONNECTED("GIT001", "GitHub 계정이 연동되지 않았습니다."),
    GITHUB_REPO_NOT_CONFIGURED("GIT002", "GitHub 저장소가 설정되지 않았습니다."),
    GITHUB_REPO_CREATE_FAILED("GIT003", "GitHub 저장소 생성에 실패했습니다."),
    GITHUB_COMMIT_FAILED("GIT004", "GitHub 커밋에 실패했습니다."),
    GITHUB_API_ERROR("GIT005", "GitHub API 호출 중 오류가 발생했습니다."),
    GITHUB_ALREADY_COMMITTED("GIT006", "이미 커밋된 제출입니다.");

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