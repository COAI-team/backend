package kr.or.kosa.backend.codeBoard.controller;

import kr.or.kosa.backend.codeBoard.exception.CodeBoardErrorCode;
import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import kr.or.kosa.backend.commons.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CodeBoardController {
    @GetMapping("/codeexception")
    public ResponseEntity<ApiResponse<String>> codeBoard(){
            throw new CustomBusinessException(CodeBoardErrorCode.UPDATE_ERROR);
    }
}
