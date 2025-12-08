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

  /**
   * 기존 분석 엔드포인트 (코드를 직접 전달하는 방식)
   * POST /api/analysis/analyze
   * Body: { "code": "...", "analysisTypes": [...], "toneLevel": 3,
   * "customRequirements": "...", "userId": 1 }
   */
  @PostMapping("/analyze")
  public ResponseEntity<String> analyzeCode(@RequestBody AnalysisRequestDTO requestDto) {
    System.out.println("[TRACE] AnalysisController.analyzeCode called with: " + requestDto);
    // In a real app, you would get the userId from the security context
    // For example:
    // Long userId = ((UserDetails)
    // SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId();
    // requestDto.setUserId(userId);

    // String result = analysisService.analyzeCode(requestDto); // Original call
    String dummyResult = """
        {
          "aiScore": 75,
          "codeSmells": [
            {
              "name": "Dummy Long Method",
              "description": "This is a dummy code smell for a long method. Please implement the actual AI."
            },
            {
              "name": "Dummy Magic Number",
              "description": "This is a dummy code smell for a magic number. Authenticate users properly."
            }
          ],
          "suggestions": [
            {
              "problematicSnippet": "Dummy problematic code",
              "proposedReplacement": "Dummy suggested replacement"
            }
          ]
        }
        """;
    return ResponseEntity.ok(dummyResult);
  }

  /**
   * 저장된 파일을 분석하는 새로운 엔드포인트 (2단계: AI 분석)
   * POST /api/analysis/analyze-stored
   * Body: {
   * "repositoryUrl": "https://github.com/user/repo",
   * "filePath": "src/main/java/Example.java",
   * "code": "실제 코드 내용...",
   * "analysisTypes": ["code_smell", "design_pattern"],
   * "toneLevel": 3,
   * "customRequirements": "특정 패턴에 집중해주세요",
   * "userId": 1
   * }
   */
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

  /**
   * 저장된 파일을 분석하는 스트리밍 엔드포인트
   * POST /api/analysis/analyze-stored/stream
   */
  @PostMapping(value = "/analyze-stored/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
  public reactor.core.publisher.Flux<String> analyzeStoredFileStream(@RequestBody AnalysisRequestDTO requestDto) {
    System.out.println("[TRACE] AnalysisController.analyzeStoredFileStream called with: " + requestDto);
    return analysisService.analyzeStoredFileStream(requestDto);
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
