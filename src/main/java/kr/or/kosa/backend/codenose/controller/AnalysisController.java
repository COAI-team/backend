package kr.or.kosa.backend.codenose.controller;

import kr.or.kosa.backend.codenose.dto.dtoReal.AnalysisRequestDTO;
import kr.or.kosa.backend.codenose.service.AnalysisService;
import kr.or.kosa.backend.commons.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
public class AnalysisController {

  private final AnalysisService analysisService;

  @PostMapping("/analyze-stored")
  public ResponseEntity<ApiResponse<String>> analyzeStoredFile(@RequestBody AnalysisRequestDTO requestDto) {
    System.out.println("[TRACE] AnalysisController.analyzeStoredFile called with: " + requestDto);
    // TODO: 실제로는 SecurityContext에서 userId를 가져와야 함
    // Long userId = ((UserDetails)
    // SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId();
    // requestDto.setUserId(userId);

    String result = analysisService.analyzeStoredFile(requestDto);
    return ResponseEntity.ok(ApiResponse.success(result));
  }

  @org.springframework.web.bind.annotation.GetMapping("/history/{userId}")
  public ResponseEntity<ApiResponse<java.util.List<kr.or.kosa.backend.codenose.dto.dtoReal.CodeResultDTO>>> getUserAnalysisHistory(
      @org.springframework.web.bind.annotation.PathVariable Long userId) {
    System.out.println("[TRACE] AnalysisController.getUserAnalysisHistory called with userId: " + userId);
    java.util.List<kr.or.kosa.backend.codenose.dto.dtoReal.CodeResultDTO> history = analysisService
        .getUserAnalysisHistory(userId);
    return ResponseEntity.ok(ApiResponse.success(history));
  }

  @org.springframework.web.bind.annotation.GetMapping("/result/{analysisId}")
  public ResponseEntity<ApiResponse<kr.or.kosa.backend.codenose.dto.dtoReal.CodeResultDTO>> getAnalysisResult(
      @org.springframework.web.bind.annotation.PathVariable String analysisId) {
    System.out.println("[TRACE] AnalysisController.getAnalysisResult called with analysisId: " + analysisId);
    kr.or.kosa.backend.codenose.dto.dtoReal.CodeResultDTO result = analysisService.getAnalysisResult(analysisId);
    return ResponseEntity.ok(ApiResponse.success(result));
  }
}
