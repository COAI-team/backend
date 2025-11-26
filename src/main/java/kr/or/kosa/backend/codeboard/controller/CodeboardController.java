package kr.or.kosa.backend.codeboard.controller;

import kr.or.kosa.backend.codeboard.exception.CodeboardErrorCode;
import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import kr.or.kosa.backend.commons.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CodeboardController {
    @GetMapping("/codeexception")
    public ResponseEntity<ApiResponse<String>> codeBoard(){
            throw new CustomBusinessException(CodeboardErrorCode.UPDATE_ERROR);
    }
}
