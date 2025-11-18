package kr.or.kosa.backend.commons.response;

public class ApiResponse<T> {
    private final String code;
    private final String message;
    private final T Data;

    public ApiResponse(String code, String message, T Data) {
        this.code = code;
        this.message = message;
        this.Data = Data;
    }

    public static <T> ApiResponse<T> success(T data){
        return new ApiResponse<T>("0000", "success", data);
    }

    public static <T> ApiResponse<T> error(String code, String message){
        return new ApiResponse<T>(code, message, null);
    }
    public String getCode() {
        return code;
    }
    public String getMessage() {
        return message;
    }

    public T getData() {
        return Data;
    }


}
