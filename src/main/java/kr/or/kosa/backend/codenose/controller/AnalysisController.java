package kr.or.kosa.backend.codenose.controller;

import kr.or.kosa.backend.codenose.dto.AnalysisRequestDTO;
import kr.or.kosa.backend.codenose.service.AnalysisService;
import kr.or.kosa.backend.commons.response.ApiResponse;
import kr.or.kosa.backend.security.jwt.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 분석 컨트롤러 (AnalysisController)
 * 
 * 역할:
 * 코드 분석과 관련된 HTTP 요청을 처리하는 진입점입니다.
 * 사용자가 저장한 파일에 대한 분석을 트리거하거나, 과거 분석 이력을 조회하는 API를 제공합니다.
 */
@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
public class AnalysisController {

  private final AnalysisService analysisService;

  /**
   * 저장된 파일 분석 요청 처리
   * 
   * @param requestDto 분석할 파일의 메타데이터 및 분석 설정 정보
   * @return 분석 결과 문자열을 포함한 응답
   * 
   *         작동 로직:
   *         1. SecurityContext에서 현재 인증된 사용자(JWT) 정보를 가져옵니다.
   *         2. 요청 DTO에 사용자 ID를 주입하여 보안성을 강화합니다.
   *         3. AnalysisService를 호출하여 실제 분석 로직(LLM 호출 등)을 수행합니다.
   */
  @PostMapping("/analyze-stored")
  public ResponseEntity<ApiResponse<String>> analyzeStoredFile(@RequestBody AnalysisRequestDTO requestDto) {
    // 인증된 사용자 정보 가져오기
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
    requestDto.setUserId(userDetails.id());

    String result = analysisService.analyzeStoredFile(requestDto);
    return ResponseEntity.ok(ApiResponse.success(result));
  }

  /**
   * 사용자별 분석 이력 조회
   * 
   * @param userId 조회할 사용자의 ID
   * @return 사용자의 과거 코드 분석 이력 리스트
   */
  @org.springframework.web.bind.annotation.GetMapping("/history/{userId}")
  public ResponseEntity<ApiResponse<java.util.List<kr.or.kosa.backend.codenose.dto.CodeResultDTO>>> getUserAnalysisHistory(
      @org.springframework.web.bind.annotation.PathVariable Long userId) {
    java.util.List<kr.or.kosa.backend.codenose.dto.CodeResultDTO> history = analysisService
        .getUserAnalysisHistory(userId);
    return ResponseEntity.ok(ApiResponse.success(history));
  }

  /**
   * 특정 분석 결과 상세 조회
   * 
   * @param analysisId 분석 결과의 고유 ID
   * @return 상세 분석 결과 데이터 (Code Smells, 제안 등 포함)
   */
  @org.springframework.web.bind.annotation.GetMapping("/result/{analysisId}")
  public ResponseEntity<ApiResponse<kr.or.kosa.backend.codenose.dto.CodeResultDTO>> getAnalysisResult(
      @org.springframework.web.bind.annotation.PathVariable String analysisId) {
    kr.or.kosa.backend.codenose.dto.CodeResultDTO result = analysisService.getAnalysisResult(analysisId);
    return ResponseEntity.ok(ApiResponse.success(result));
  }
}
