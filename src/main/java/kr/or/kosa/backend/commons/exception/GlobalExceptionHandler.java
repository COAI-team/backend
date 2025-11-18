package kr.or.kosa.backend.commons.exception;

import kr.or.kosa.backend.commons.exception.base.BaseBusinessException;
import kr.or.kosa.backend.commons.exception.base.BaseSystemException;
import kr.or.kosa.backend.commons.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;

import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseBusinessException.class)
    public ResponseEntity<ApiResponse<Void>>handleBaseSystemException(
        BaseBusinessException ex
    ){
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(ex.getErrorCode().getCode(), ex.getMessage()));
    }

    @ExceptionHandler(BaseSystemException.class)
    public ResponseEntity<ApiResponse<Void>> handleBaseSystemException(
        BaseSystemException ex
    ){
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(ex.getErrorCode().getCode(), ex.getMessage()));
    }

    // controller에서 들어오는 파라미터 값에 대해서 valid, notnull, blank 일때 니오는 exception
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>>handleMethodArgumentNotValidException(
        MethodArgumentNotValidException ex
    ){
        String errorMessage = ex.getBindingResult()
            .getAllErrors()
            .get(0)
            .getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("VALIDATION ERROR", errorMessage));
    }
}