package kr.or.kosa.backend.commons.response;

public record ApiResponse<T>(String code, String message, T Data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("0000", "success", data);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}