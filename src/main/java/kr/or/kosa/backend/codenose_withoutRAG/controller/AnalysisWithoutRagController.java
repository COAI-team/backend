package kr.or.kosa.backend.codenose_withoutRAG.controller;

import kr.or.kosa.backend.codenose.dto.AnalysisRequestDTO;
import kr.or.kosa.backend.codenose.dto.CodeResultDTO;
import kr.or.kosa.backend.codenose_withoutRAG.service.AnalysisWithoutRagService;
import kr.or.kosa.backend.commons.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analysis/norag")
@RequiredArgsConstructor
public class AnalysisWithoutRagController {

    private final AnalysisWithoutRagService analysisService;

    /**
     * 기존 분석 엔드포인트 (코드를 직접 전달하는 방식) - RAG 제외
     * POST /api/analysis/norag/analyze
     */
    @PostMapping("/analyze")
    public ResponseEntity<String> analyzeCode(@RequestBody AnalysisRequestDTO requestDto) {
        System.out.println("[TRACE] AnalysisWithoutRagController.analyzeCode called with: " + requestDto);
        String result = analysisService.analyzeCode(null, null, requestDto); // userId, userMessage not used in simple
                                                                             // analysis
        return ResponseEntity.ok(result);
    }

    /**
     * 저장된 파일을 분석하는 새로운 엔드포인트 (2단계: AI 분석) - RAG 제외
     * POST /api/analysis/norag/analyze-stored
     */
    @PostMapping("/analyze-stored")
    public ResponseEntity<ApiResponse<String>> analyzeStoredFile(@RequestBody AnalysisRequestDTO requestDto) {
        System.out.println("[TRACE] AnalysisWithoutRagController.analyzeStoredFile called with: " + requestDto);
        String result = analysisService.analyzeStoredFile(requestDto);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

}
