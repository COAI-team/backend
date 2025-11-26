package kr.or.kosa.backend.commons.response;

public record ApiResponse<T>(String code, String message, T data) {

    // 성공 응답
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("0000", "success", data);
    }

    // 성공 응답 (데이터 없음)
    public static ApiResponse<Void> success() {
        return new ApiResponse<>("0000", "success", null);
    }

    // 에러 응답 (데이터 없음)
    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    // 에러 응답 (데이터 포함) - Validation 에러 등에 사용
    public static <T> ApiResponse<T> error(String code, String message, T data) {
        return new ApiResponse<>(code, message, data);
    }
}